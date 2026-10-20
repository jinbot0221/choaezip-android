'use strict';
const {onCall, HttpsError} = require('firebase-functions/v2/https');
const {onSchedule} = require('firebase-functions/v2/scheduler');
const {initializeApp} = require('firebase-admin/app');
const {getFirestore, FieldValue} = require('firebase-admin/firestore');
const {getAuth} = require('firebase-admin/auth');
const {getStorage} = require('firebase-admin/storage');
const {getMessaging} = require('firebase-admin/messaging');
const crypto = require('node:crypto');
const {koreaDay,reminderEligible,communityQuietHours,anniversaryDates,validMediaPath}=require('./policy');
initializeApp();
const db = getFirestore();
const fail = (code, message) => { throw new HttpsError(code, message); };
const need = (test, message) => { if (!test) fail('invalid-argument', message); };
const text = (v, min, max, label) => { need(typeof v === 'string' && v.trim().length >= min && v.trim().length <= max, `${label}: ${min}~${max}자`); return v.trim(); };
const id = v => { need(typeof v === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(v), '잘못된 ID'); return v; };
const defaults = {schedule:true, comment:true, like:false, memory:false};
const digest = (...v) => crypto.createHash('sha256').update(v.join(':')).digest('hex');
const publicProfile = (uid, u) => ({id:uid, nickname:u?.completed && !u.deleted ? u.nickname : '탈퇴한 사용자', bio:u?.completed && !u.deleted ? u.bio || '' : ''});
async function user(uid, write = false) {
  if (!uid) fail('unauthenticated', '로그인이 필요해요.');
  const s = await db.doc(`users/${uid}`).get();
  if (!s.exists || !s.data().completed || s.data().deleted) fail('permission-denied', '가입 완료가 필요해요.');
  if (write && s.data().restricted) fail('permission-denied', 'ACCOUNT_RESTRICTED');
  return s.data();
}
async function blocked(a,b) {
  if (!a || !b || a === b) return false;
  const s = await db.getAll(db.doc(`users/${a}/blocks/${b}`),db.doc(`users/${b}/blocks/${a}`));
  return s.some(x=>x.exists);
}
async function visiblePost(pid, uid) {
  const s = await db.doc(`posts/${id(pid)}`).get();
  if (!s.exists || s.data().hidden || await blocked(uid,s.data().ownerId)) fail('not-found','글을 찾을 수 없어요.');
  const author=await db.doc(`users/${s.data().ownerId}`).get();
  if(!author.data()?.completed||author.data()?.deleted) fail('not-found','글을 찾을 수 없어요.');
  return {id:s.id,...s.data()};
}
async function assertUnblocked(tx, a, b) {
  if(a===b) return;
  const snapshots=await Promise.all([tx.get(db.doc(`users/${a}/blocks/${b}`)),tx.get(db.doc(`users/${b}/blocks/${a}`))]);
  if(snapshots.some(s=>s.exists)) fail('permission-denied','차단 관계에서는 반응할 수 없어요.');
}
async function favoriteArtist(artistId) {
  const s = await db.doc(`artists/${id(artistId)}`).get();
  need(s.exists && s.data().active, '활성 아티스트를 선택해 주세요.');
}
async function event(tx, recipient, eventId, data) {
  const key = digest(recipient,eventId);
  tx.set(db.doc(`users/${recipient}/notifications/${key}`), {id:key,recipient,read:false,createdAt:Date.now(),...data});
  tx.set(db.doc(`outbox/${key}`), {recipient,notificationId:key,status:'pending',attempts:0,nextAttemptAt:Date.now(),...data});
}
async function mediaPaths(uid, paths, purpose) {
  need(Array.isArray(paths) && paths.length <= 4, '사진은 최대 4장이에요.');
  for (const path of paths) {
    need(validMediaPath(uid,path),'본인 사진만 사용할 수 있어요.');
    const file = getStorage().bucket().file(path);
    const [meta] = await file.getMetadata().catch(()=>fail('invalid-argument','사진 업로드를 확인해 주세요.'));
    need(Number(meta.size)<=10*1024*1024 && ['image/jpeg','image/png','image/webp'].includes(meta.contentType),'사진 형식 또는 크기가 잘못됐어요.');
    need(meta.metadata?.purpose===purpose,'사진의 사용 목적이 일치하지 않아요.');
    const [header]=await file.download({start:0,end:11});
    const valid=meta.contentType==='image/jpeg'?header[0]===0xff&&header[1]===0xd8&&header[2]===0xff:meta.contentType==='image/png'?header.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])):header.subarray(0,4).toString()==='RIFF'&&header.subarray(8,12).toString()==='WEBP';
    need(valid,'사진 파일 내용을 확인해 주세요.');
  }
  return paths;
}
async function deleteUnusedMedia(uid, paths) {
  for(const path of paths) {
    let used=false;
    for(const collection of ['records','posts']) {
      const s=await db.collection(collection).where('ownerId','==',uid).where('media','array-contains',path).limit(1).get();
      if(!s.empty) used=true;
    }
    if(!used) await getStorage().bucket().file(path).delete({ignoreNotFound:true});
  }
}
async function versioned(ref, uid, expectedVersion, updates, remove=false) {
  await db.runTransaction(async tx=>{
    const s = await tx.get(ref);
    if (!s.exists || s.data().ownerId !== uid) fail('not-found','대상을 찾을 수 없어요.');
    if (s.data().hidden) fail('permission-denied','숨김 처리된 글은 변경할 수 없어요.');
    if (s.data().version !== expectedVersion) fail('aborted','VERSION_CONFLICT: 최신 내용을 확인해 주세요.');
    if (remove) tx.delete(ref); else tx.update(ref,{...updates,version:expectedVersion+1,updatedAt:Date.now()});
  });
}
async function page(query, input, orderFields=['createdAt']) {
  const limit = Math.max(1,Math.min(Number(input.limit)||20,50));
  for(const field of orderFields) query=query.orderBy(field,'desc');
  query=query.orderBy('__name__','desc');
  if (input.cursor) {
    let c; try { c=JSON.parse(Buffer.from(input.cursor,'base64url').toString()); } catch { fail('invalid-argument','잘못된 cursor'); }
    need(Array.isArray(c) && c.length===orderFields.length+1 && c.every(x=>typeof x==='string'||Number.isFinite(x)),'잘못된 cursor');
    query=query.startAfter(...c);
  }
  const s = await query.limit(limit).get();
  return {items:s.docs.map(d=>({id:d.id,...d.data()})),cursor:s.size===limit?Buffer.from(JSON.stringify([...orderFields.map(f=>s.docs.at(-1).data()[f]),s.docs.at(-1).id])).toString('base64url'):null};
}
exports.api = onCall({region:'asia-northeast3',maxInstances:10},async req=> {
  const p = req.data || {}, action = p.action, uid = req.auth?.uid;
  if (uid) {
    const raw = req.rawRequest.headers.authorization || '';
    await getAuth().verifyIdToken(raw.replace(/^Bearer /, ''), true).catch(()=>fail('unauthenticated','세션이 만료됐어요. 다시 로그인해 주세요.'));
  }
  if (action === 'adminSchedule') {
    if (!req.auth?.token.admin) fail('permission-denied','운영자 권한이 필요해요.');
    const sid=id(p.id), ref=db.doc(`schedules/${sid}`);
    await favoriteArtist(p.artistId);
    need(['방송','컴백','공연','티켓'].includes(p.type) && ['예정','취소','완료'].includes(p.status),'일정 분류와 상태를 확인해 주세요.');
    need(Number.isFinite(p.startAt) && p.startAt>0,'시작 시간을 확인해 주세요.');
    need(typeof p.sourceUrl==='string' && /^https:\/\/[^\s]+$/.test(p.sourceUrl),'HTTPS 공식 출처가 필요해요.');
    const fields={artistId:p.artistId,title:text(p.title,2,120,'일정명'),body:text(p.body||'',0,5000,'설명'),location:text(p.location||'',0,200,'장소'),sourceName:text(p.sourceName,1,100,'출처'),sourceUrl:p.sourceUrl,startAt:p.startAt,type:p.type,status:p.status};
    await db.runTransaction(async tx=>{
      const old=await tx.get(ref);
      if(old.exists && old.data().version!==p.expectedVersion) fail('aborted','SCHEDULE_CHANGED');
      const reminders=await tx.get(db.collectionGroup('reminders').where('scheduleId','==',sid).where('active','==',true));
      const version=(old.data()?.version||0)+1;
      tx.set(ref,{...fields,version,updatedAt:Date.now()});
      tx.set(ref.collection('history').doc(String(version)),{...fields,version,operator:uid,createdAt:Date.now()});
      for(const r of reminders.docs) {
        const recipient=r.ref.parent.parent.id;
        const canceled=p.status==='취소', changed=old.exists&&(old.data().startAt!==p.startAt||old.data().location!==fields.location);
        tx.update(r.ref,{scheduleVersion:version,version:r.data().version+1,active:p.status==='예정'});
        if(canceled||changed) await event(tx,recipient,`schedule-change:${sid}:${version}`,{group:'schedule',title:canceled?'신청한 일정이 취소됐어요':'신청한 일정이 변경됐어요',body:`${fields.title}의 최신 안내를 확인해 주세요.`,entityType:'schedule',entityId:sid,expiresAt:Date.now()+(canceled?24:6)*3600000});
      }
    }); return {id:sid};
  }
  if(action==='adminReports') {
    if(!req.auth?.token.admin) fail('permission-denied','운영자 권한이 필요해요.');
    return page(db.collection('reports'),p);
  }
  if(action==='adminReviewReport') {
    if(!req.auth?.token.admin) fail('permission-denied','운영자 권한이 필요해요.');
    need(['action_taken','no_violation'].includes(p.outcome),'처리 결과를 확인해 주세요.');
    await db.runTransaction(async tx=>{
      const ref=db.doc(`reports/${id(p.id)}`), report=await tx.get(ref);
      if(!report.exists) fail('not-found','신고를 찾을 수 없어요.');
      if(report.data().status==='reviewed') return;
      const r=report.data(), target=db.doc(r.targetType==='post'?`posts/${r.targetId}`:`posts/${r.postId}/comments/${r.targetId}`), content=await tx.get(target);
      if(p.outcome==='action_taken'&&content.exists) tx.update(target,{hidden:true});
      tx.update(ref,{status:'reviewed',outcome:p.outcome,reviewedAt:Date.now(),operator:uid});
      await event(tx,r.reporterId,`report:${report.id}`,{group:'account',title:'접수한 신고의 처리가 완료됐어요',body:p.outcome==='action_taken'?'서비스 운영 기준에 따라 조치했습니다.':'검토 결과 위반을 확인하지 못했습니다.',entityType:'notifications',entityId:report.id,expiresAt:Date.now()+86400000});
    }); return {ok:true};
  }
  if (action==='artists') {
    const s = await db.collection('artists').where('active','==',true).get();
    return {items:s.docs.map(d=>({id:d.id,...d.data()})).filter(a=>!p.q || a.name.toLowerCase().includes(String(p.q).toLowerCase()))};
  }
  if (action==='schedules') {
    need(/^\d{4}-\d{2}$/.test(p.month),'월을 선택해 주세요.');
    const from = Date.parse(`${p.month}-01T00:00:00+09:00`);
    const [y,m]=p.month.split('-').map(Number);
    const to=Date.UTC(y,m,1)-9*3600000;
    const s = await db.collection('schedules').where('startAt','>=',from).where('startAt','<',to).orderBy('startAt').get();
    return {items:s.docs.map(d=>({id:d.id,...d.data()})).filter(x=>(!p.artistId || x.artistId===p.artistId)&&(!p.type || x.type===p.type)).sort((a,b)=>a.startAt-b.startAt||a.id.localeCompare(b.id)),updatedAt:Date.now()};
  }
  if (action==='schedule') {
    const s=await db.doc(`schedules/${id(p.id)}`).get();
    if(!s.exists) fail('not-found','삭제된 일정이에요.');
    const reminder=uid?(await db.doc(`users/${uid}/reminders/${s.id}`).get()).data():null;
    return {id:s.id,...s.data(),reminded:!!reminder?.active};
  }
  if (action==='posts') {
    let q=db.collection('posts').where('hidden','==',false);
    if (p.artistId) q=q.where('artistId','==',id(p.artistId));
    if (p.authorMe) { await user(uid); q=q.where('ownerId','==',uid); }
    if (p.category) q=q.where('category','==',p.category);
    const result=await page(q,p);
    result.items=(await Promise.all(result.items.map(async post=>{ const owner=(await db.doc(`users/${post.ownerId}`).get()).data(); return !owner?.completed||owner.deleted||await blocked(uid,post.ownerId)?null:{...post,author:publicProfile(post.ownerId,owner)}; }))).filter(Boolean);
    return result;
  }
  if(action==='post') {
    const post=await visiblePost(p.id,uid);
    const comments=await page(db.collection(`posts/${post.id}/comments`).where('hidden','==',false),p);
    comments.items=(await Promise.all(comments.items.map(async c=>await blocked(uid,c.ownerId)?null:{...c,author:publicProfile(c.ownerId,(await db.doc(`users/${c.ownerId}`).get()).data())}))).filter(Boolean);
    const liked=uid?(await db.doc(`posts/${post.id}/likes/${uid}`).get()).exists:false;
    return {...post,liked,author:publicProfile(post.ownerId,(await db.doc(`users/${post.ownerId}`).get()).data()),comments};
  }
  if(action==='media') {
    let item;
    if(p.kind==='record') { await user(uid); const s=await db.doc(`records/${id(p.id)}`).get(); if(!s.exists||s.data().ownerId!==uid) fail('not-found','기록을 찾을 수 없어요.'); item=s.data(); }
    else item=await visiblePost(p.id,uid);
    const urls=await Promise.all((item.media||[]).map(async path=>{
      const file=getStorage().bucket().file(path);
      // Emulator URLs are fetched through the callable as base64; no public download token is minted.
      if(process.env.FUNCTIONS_EMULATOR==='true') { const [b]=await file.download(); const [m]=await file.getMetadata(); return `data:${m.contentType};base64,${b.toString('base64')}`; }
      return (await file.getSignedUrl({action:'read',expires:Date.now()+5*60000}))[0];
    })); return {urls};
  }
  if(action==='comments') {
    await visiblePost(p.id,uid);
    const result=await page(db.collection(`posts/${id(p.id)}/comments`).where('hidden','==',false),p);
    result.items=(await Promise.all(result.items.map(async c=>await blocked(uid,c.ownerId)?null:{...c,author:publicProfile(c.ownerId,(await db.doc(`users/${c.ownerId}`).get()).data())}))).filter(Boolean);
    return result;
  }
  if(action==='completeSignup') {
    if(!uid) fail('unauthenticated','로그인이 필요해요.');
    const nickname=text(p.nickname,2,20,'닉네임');
    need(p.terms===true && p.privacy===true,'필수 약관에 동의해 주세요.');
    await db.runTransaction(async tx=>{
      const ref=db.doc(`users/${uid}`), s=await tx.get(ref);
      if(s.data()?.deleted) fail('permission-denied','탈퇴한 계정이에요.');
      if(!s.data()?.completed) tx.set(ref,{nickname,bio:'',completed:true,deleted:false,restricted:false,termsVersion:'1.0',privacyVersion:'1.0',consentedAt:Date.now(),createdAt:Date.now(),preferences:defaults});
    });
    return {ok:true};
  }
  const me=await user(uid, !['me','records','record','notifications','blocks','preferences','media','logout','deleteAccount'].includes(action));
  if(action==='me') return {id:uid,...me};
  if(action==='profile') { await db.doc(`users/${uid}`).update({nickname:text(p.nickname,2,20,'닉네임'),bio:text(p.bio||'',0,100,'소개')}); return {ok:true}; }
  if(action==='favorite') { await favoriteArtist(p.artistId); await db.doc(`users/${uid}`).update({artistId:p.artistId}); return {ok:true}; }
  if(action==='records') { let q=db.collection('records').where('ownerId','==',uid); if(p.artistId) q=q.where('artistId','==',id(p.artistId)); return page(q,p,['date','createdAt']); }
  if(action==='record') { const s=await db.doc(`records/${id(p.id)}`).get(); if(!s.exists || s.data().ownerId!==uid) fail('not-found','기록을 찾을 수 없어요.'); return {id:s.id,...s.data()}; }
  if(action==='saveRecord' || action==='savePost') {
    const record=action==='saveRecord';
    const fields={title:text(p.title,2,record?50:80,'제목'),body:text(p.body,1,record?2000:3000,'본문'),artistId:id(p.artistId),category:p.category,media:await mediaPaths(uid,p.media||[],record?'record':'post')};
    await favoriteArtist(fields.artistId);
    need((record?['공연','방송','일상','굿즈']:['자유 이야기','공연 후기']).includes(fields.category),'분류를 선택해 주세요.');
    if(record) { need(/^\d{4}-\d{2}-\d{2}$/.test(p.date) && !isNaN(Date.parse(p.date)) && new Date(p.date).toISOString().slice(0,10)===p.date,'날짜를 확인해 주세요.'); fields.date=p.date; }
    const collection=record?'records':'posts';
    if(p.id) {
      const ref=db.doc(`${collection}/${id(p.id)}`), old=(await ref.get()).data();
      await versioned(ref,uid,p.expectedVersion,fields);
      await deleteUnusedMedia(uid,(old?.media||[]).filter(path=>!fields.media.includes(path)));
      return {id:p.id};
    }
    id(p.requestKey);
    const key=digest(uid,action,p.requestKey), ref=db.doc(`${collection}/${key}`), request=db.doc(`requests/${key}`);
    await db.runTransaction(async tx=>{
      const prior=await tx.get(request); if(prior.exists) return;
      tx.create(ref,{...fields,ownerId:uid,createdAt:Date.now(),updatedAt:Date.now(),version:1,...(!record?{hidden:false,likeCount:0}: {})});
      tx.create(request,{createdAt:Date.now(),expiresAt:Date.now()+86400000});
    });
    return {id:key};
  }
  if(action==='deleteRecord' || action==='deletePost') {
    const collection=action==='deleteRecord'?'records':'posts', ref=db.doc(`${collection}/${id(p.id)}`), old=(await ref.get()).data();
    await versioned(ref,uid,p.expectedVersion,{},true);
    await db.recursiveDelete(ref);
    await deleteUnusedMedia(uid,old?.media||[]);
    return {ok:true};
  }
  if(action==='like') {
    const post=await visiblePost(p.id,uid), ref=db.doc(`posts/${post.id}`), like=ref.collection('likes').doc(uid);
    await db.runTransaction(async tx=>{
      const day=koreaDay(Date.now()), eventId=`like:${post.id}:${uid}:${day}`;
      const marker=db.doc(`events/${digest(eventId)}`);
      const [s,l,m]=await Promise.all([tx.get(ref),tx.get(like),tx.get(marker)]);
      if(!s.exists || s.data().hidden) fail('not-found','삭제 또는 숨김된 글이에요.');
      await assertUnblocked(tx,uid,post.ownerId);
      if(!!p.enabled===l.exists) return;
      if(p.enabled) tx.set(like,{ownerId:uid,createdAt:Date.now()}); else tx.delete(like);
      tx.update(ref,{likeCount:Math.max(0,(s.data().likeCount||0)+(p.enabled?1:-1))});
      if(p.enabled && post.ownerId!==uid && !m.exists) {
        tx.create(marker,{createdAt:Date.now()});
        // Notification ID is stable across unlike/re-like on the same Korean day.
        await event(tx,post.ownerId,eventId,{group:'like',title:'내 이야기에 공감이 도착했어요',body:`${me.nickname} 님이 내 이야기를 좋아해요.`,entityType:'post',entityId:post.id,actorId:uid,expiresAt:Date.now()+3600000});
      }
    }); return {ok:true};
  }
  if(action==='comment') {
    const post=await visiblePost(p.id,uid), body=text(p.body,1,500,'댓글'); id(p.requestKey);
    const cid=digest(uid,post.id,p.requestKey), ref=db.doc(`posts/${post.id}/comments/${cid}`);
    await db.runTransaction(async tx=>{
      const [s,latest]=await Promise.all([tx.get(ref),tx.get(db.doc(`posts/${post.id}`))]);
      if(s.exists) return;
      await assertUnblocked(tx,uid,post.ownerId);
      if(!latest.exists || latest.data().hidden) fail('not-found','삭제 또는 숨김된 글이에요.');
      tx.create(ref,{ownerId:uid,body,hidden:false,createdAt:Date.now()});
      if(post.ownerId!==uid) await event(tx,post.ownerId,`comment:${cid}`,{group:'comment',title:'내 이야기에 댓글이 달렸어요',body:`${me.nickname} 님이 댓글을 남겼어요.`,entityType:'post',entityId:post.id,commentId:cid,actorId:uid,expiresAt:Date.now()+6*3600000});
    }); return {id:cid};
  }
  if(action==='deleteComment') {
    await visiblePost(p.postId,uid);
    const ref=db.doc(`posts/${id(p.postId)}/comments/${id(p.id)}`), s=await ref.get();
    if(!s.exists || s.data().ownerId!==uid) fail('not-found','댓글을 찾을 수 없어요.');
    await ref.delete(); return {ok:true};
  }
  if(action==='reminder') {
    const ref=db.doc(`schedules/${id(p.id)}`), reminder=db.doc(`users/${uid}/reminders/${p.id}`);
    await db.runTransaction(async tx=>{
      const s=await tx.get(ref), old=await tx.get(reminder);
      if(!s.exists) fail('not-found','삭제된 일정이에요.');
      if(p.expectedVersion!==s.data().version) fail('aborted','SCHEDULE_CHANGED');
      if(p.enabled) need(reminderEligible(s.data(),Date.now()),'시작 30분 전까지만 신청할 수 있어요.');
      tx.set(reminder,{scheduleId:p.id,active:!!p.enabled,scheduleVersion:s.data().version,version:(old.data()?.version||0)+1,createdAt:Date.now()});
    }); return {ok:true};
  }
  if(action==='preferences') {
    if(p.values) {
      const values={...defaults,...me.preferences};
      for(const key of Object.keys(defaults)) if(typeof p.values[key]==='boolean') values[key]=p.values[key];
      await db.doc(`users/${uid}`).update({preferences:values}); return values;
    } return {...defaults,...me.preferences};
  }
  if(action==='notifications') return page(db.collection(`users/${uid}/notifications`).where('createdAt','>=',Date.now()-30*86400000),p);
  if(action==='readNotification') { const ref=db.doc(`users/${uid}/notifications/${id(p.id)}`); if(!(await ref.get()).exists) fail('not-found','알림을 찾을 수 없어요.'); await ref.update({read:true}); return {ok:true}; }
  if(action==='device') {
    const ref=db.doc(`users/${uid}/devices/${id(p.installationId)}`);
    if(p.token) await ref.set({token:text(p.token,1,4096,'기기 토큰'),permission:!!p.permission,updatedAt:Date.now()}); else await ref.delete();
    return {ok:true};
  }
  if(action==='logout') { if(p.installationId) await db.doc(`users/${uid}/devices/${id(p.installationId)}`).delete(); return {ok:true}; }
  if(action==='blocks') {
    const s=await db.collection(`users/${uid}/blocks`).get();
    return {items:await Promise.all(s.docs.map(async d=>publicProfile(d.id,(await db.doc(`users/${d.id}`).get()).data())))};
  }
  if(action==='block') {
    id(p.userId); need(uid!==p.userId,'본인은 차단할 수 없어요.');
    const ref=db.doc(`users/${uid}/blocks/${p.userId}`);
    if(p.enabled) await ref.set({createdAt:Date.now()}); else await ref.delete();
    return {ok:true};
  }
  if(action==='report') {
    need(['post','comment'].includes(p.targetType),'잘못된 신고 대상');
    const post=await visiblePost(p.postId||p.targetId,uid);
    let target=post;
    if(p.targetType==='comment') { const s=await db.doc(`posts/${post.id}/comments/${id(p.targetId)}`).get(); if(!s.exists) fail('not-found','삭제된 댓글이에요.'); target=s.data(); }
    need(target.ownerId!==uid,'본인은 신고할 수 없어요.');
    need(['스팸','욕설','개인정보','권리침해','기타'].includes(p.reason),'신고 사유를 선택해 주세요.');
    const detail=text(p.detail||'',0,500,'상세 설명'), key=digest(uid,p.targetType,p.targetId);
    await db.runTransaction(async tx=>{ const ref=db.doc(`reports/${key}`); if(!(await tx.get(ref)).exists) tx.create(ref,{reporterId:uid,targetType:p.targetType,targetId:id(p.targetId),postId:post.id,ownerId:target.ownerId,reason:p.reason,detail,status:'pending',createdAt:Date.now()}); });
    return {id:key,status:(await db.doc(`reports/${key}`).get()).data().status};
  }
  if(action==='deleteAccount') {
    need(p.confirmation===true,'탈퇴에 동의해 주세요.');
    need(req.auth.token.auth_time*1000>Date.now()-5*60000,'최근 5분 이내 재인증이 필요해요.');
    const batch=db.batch(); batch.update(db.doc(`users/${uid}`),{deleted:true,completed:false}); batch.set(db.doc(`deletions/${uid}`),{createdAt:Date.now(),status:'pending'}); await batch.commit();
    await getAuth().updateUser(uid,{disabled:true});
    await getAuth().revokeRefreshTokens(uid);
    // A durable job remains if synchronous cleanup fails.
    await cleanupAccount(uid).catch(()=>{});
    return {ok:true};
  }
  fail('invalid-argument','지원하지 않는 요청이에요.');
});

async function cleanupAccount(uid) {
  await getAuth().updateUser(uid,{disabled:true}).catch(e=>{if(e.code!=='auth/user-not-found') throw e;});
  await getAuth().revokeRefreshTokens(uid).catch(e=>{if(e.code!=='auth/user-not-found') throw e;});
  for(const name of ['records','posts']) {
    const s=await db.collection(name).where('ownerId','==',uid).get();
    for(const d of s.docs) await db.recursiveDelete(d.ref);
  }
  for(const name of ['comments','likes']) {
    const s=await db.collectionGroup(name).where('ownerId','==',uid).get();
    for(const d of s.docs) {
      if(name==='likes') await db.runTransaction(async tx=>{
        const parent=d.ref.parent.parent, [p,l]=await Promise.all([tx.get(parent),tx.get(d.ref)]);
        if(l.exists) { tx.delete(d.ref); if(p.exists) tx.update(parent,{likeCount:Math.max(0,(p.data().likeCount||0)-1)}); }
      }); else await d.ref.delete();
    }
  }
  await getStorage().bucket().deleteFiles({prefix:`uploads/${uid}/`});
  await db.recursiveDelete(db.doc(`users/${uid}`));
  await getAuth().deleteUser(uid).catch(e=>{if(e.code!=='auth/user-not-found') throw e;});
  await db.doc(`deletions/${uid}`).update({status:'done'});
}

// One scheduled worker handles durable reminders, deletion retries and push dispatch.
exports.dispatch = onSchedule({schedule:'every 1 minutes',region:'asia-northeast3',timeZone:'Asia/Seoul'},async()=>{
  const deletions=await db.collection('deletions').where('status','==','pending').get();
  for(const d of deletions.docs) await cleanupAccount(d.id).catch(()=>{});
  const reminders=await db.collectionGroup('reminders').where('active','==',true).get();
  for(const d of reminders.docs) {
    const uid=d.ref.parent.parent.id, schedule=await db.doc(`schedules/${d.id}`).get();
    if(!schedule.exists || schedule.data().status!=='예정') { await d.ref.update({active:false}); continue; }
    const s=schedule.data(), r=d.data();
    if(s.startAt-1800000<=Date.now() && s.startAt>Date.now() && r.scheduleVersion===s.version) {
      const key=digest('schedule',d.id,uid,s.version,r.version), marker=db.doc(`events/${key}`);
      await db.runTransaction(async tx=>{
        const [m,current,rs]=await Promise.all([tx.get(marker),tx.get(schedule.ref),tx.get(d.ref)]);
        if(m.exists||!current.exists||current.data().version!==s.version||!rs.data()?.active||rs.data().version!==r.version) return;
        tx.create(marker,{createdAt:Date.now()});
        await event(tx,uid,key,{group:'schedule',title:'최애 일정이 곧 시작돼요',body:`${s.title} · 30분 뒤 시작이에요.`,entityType:'schedule',entityId:d.id,scheduleVersion:s.version,reminderVersion:r.version,expiresAt:s.startAt});
      });
    }
  }
  const korea=new Date(Date.now()+9*3600000), day=koreaDay(Date.now());
  if(korea.getUTCHours()===20) {
    const dates=anniversaryDates(Date.now());
    const candidates=await db.collection('records').where('date','in',dates).get();
    const latest=new Map();
    for(const d of candidates.docs) if(!latest.has(d.data().ownerId)||latest.get(d.data().ownerId).data().createdAt<d.data().createdAt) latest.set(d.data().ownerId,d);
    for(const [uid,d]of latest) {
      const u=(await db.doc(`users/${uid}`).get()).data();
      if(!u?.completed||!u.preferences?.memory) continue;
      const marker=db.doc(`events/${digest('memory',uid,day)}`);
      await db.runTransaction(async tx=>{
        if((await tx.get(marker)).exists) return;
        tx.create(marker,{createdAt:Date.now()});
        await event(tx,uid,`memory:${day}`,{group:'memory',title:'그날의 덕질을 다시 만나보세요',body:'1년 전 오늘, 소중한 기록을 남겼어요.',entityType:'record',entityId:d.id,expiresAt:Date.now()+4*3600000});
      });
    }
  }
  const queue=await db.collection('outbox').where('status','==','pending').limit(200).get();
  for(const q of queue.docs) {
    const e=q.data(); if(e.nextAttemptAt>Date.now()) continue;
    const u=(await db.doc(`users/${e.recipient}`).get()).data();
    let allowed=u?.completed&&!u.deleted&&e.expiresAt>Date.now()&&u.preferences?.[e.group]!==false;
    if(['comment','like','memory'].includes(e.group)&&communityQuietHours(Date.now())) allowed=false;
    if(e.actorId&&await blocked(e.recipient,e.actorId)) allowed=false;
    if(e.entityType==='post') {
      try { await visiblePost(e.entityId,e.recipient); } catch { allowed=false; }
      if(e.commentId&&!(await db.doc(`posts/${e.entityId}/comments/${e.commentId}`).get()).exists) allowed=false;
    }
    if(e.entityType==='record') { const r=await db.doc(`records/${e.entityId}`).get(); if(!r.exists||r.data().ownerId!==e.recipient) allowed=false; }
    if(e.entityType==='schedule'&&e.reminderVersion) {
      const [s,r]=await db.getAll(db.doc(`schedules/${e.entityId}`),db.doc(`users/${e.recipient}/reminders/${e.entityId}`));
      if(!s.exists||s.data().status!=='예정'||s.data().version!==e.scheduleVersion||!r.data()?.active||r.data().version!==e.reminderVersion) allowed=false;
    }
    if(!allowed) { await q.ref.update({status:'suppressed'}); continue; }
    const devices=await db.collection(`users/${e.recipient}/devices`).get();
    try {
      for(const d of devices.docs) {
        if(!d.data().permission) continue;
        try { await getMessaging().send({token:d.data().token,data:{notificationId:e.notificationId,entityType:e.entityType,entityId:e.entityId,recipientRef:e.recipient,title:e.title,body:e.body,expiresAt:String(e.expiresAt),sentAt:String(Date.now())},android:{priority:'high',ttl:Math.max(0,e.expiresAt-Date.now())}}); }
        catch(err) { if(['messaging/registration-token-not-registered','messaging/invalid-registration-token'].includes(err.code)) await d.ref.delete(); else throw err; }
      }
      await q.ref.update({status:'sent'});
    } catch {
      const attempts=e.attempts+1;
      await q.ref.update({attempts,status:attempts>3?'failed':'pending',nextAttemptAt:Date.now()+[30000,120000,300000][Math.min(attempts-1,2)]});
    }
  }
  // Remove unreferenced uploads after 24h (never issue long-lived media URLs).
  const [files]=await getStorage().bucket().getFiles({prefix:'uploads/'});
  for(const f of files) {
    if(Date.parse(f.metadata.timeCreated)>Date.now()-86400000) continue;
    const uid=f.name.split('/')[1]; let used=false;
    for(const collection of ['records','posts']) {
      const s=await db.collection(collection).where('ownerId','==',uid).where('media','array-contains',f.name).limit(1).get();
      if(!s.empty) used=true;
    }
    if(!used) await f.delete({ignoreNotFound:true});
  }
});
