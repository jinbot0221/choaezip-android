'use strict';
const {test,before,after}=require('node:test');
const assert=require('node:assert/strict');
const {initializeApp,deleteApp}=require('firebase/app');
const {getAuth,connectAuthEmulator,createUserWithEmailAndPassword,signInWithEmailAndPassword,sendPasswordResetEmail,signOut}=require('firebase/auth');
const {getFunctions,connectFunctionsEmulator,httpsCallable}=require('firebase/functions');
const {getFirestore,doc,getDoc,setDoc,connectFirestoreEmulator}=require('firebase/firestore');
const {getStorage,connectStorageEmulator,ref,uploadBytes,getBytes}=require('firebase/storage');
const {initializeApp:adminApp}=require('firebase-admin/app');
const {getFirestore:adminDb}=require('firebase-admin/firestore');
const {getAuth:adminAuth}=require('firebase-admin/auth');
const projectId='demo-choaezip';
process.env.FIRESTORE_EMULATOR_HOST ||= '127.0.0.1:8080';
process.env.FIREBASE_AUTH_EMULATOR_HOST ||= '127.0.0.1:9099';
process.env.FIREBASE_STORAGE_EMULATOR_HOST ||= '127.0.0.1:9199';
adminApp({projectId,storageBucket:projectId+'.appspot.com'});
const db=adminDb();
const clients=[];
function client(name){
 const app=initializeApp({projectId,apiKey:'demo-api-key',appId:'demo',storageBucket:projectId+'.appspot.com'},name);
 const auth=getAuth(app);connectAuthEmulator(auth,'http://127.0.0.1:9099',{disableWarnings:true});
 const functions=getFunctions(app,'asia-northeast3');connectFunctionsEmulator(functions,'127.0.0.1',5001);
 const firestore=getFirestore(app);connectFirestoreEmulator(firestore,'127.0.0.1',8080);
 const storage=getStorage(app);connectStorageEmulator(storage,'127.0.0.1',9199);
 const result={app,auth,firestore,storage,call:async(action,data={})=>(await httpsCallable(functions,'api')({action,...data})).data};
 clients.push(result);return result;
}
const a=client('a-'+Date.now()),b=client('b-'+Date.now()),guest=client('guest-'+Date.now());
let aid,bid,record,post,comment;
const stamp=Date.now();
before(async()=>{
 await db.doc('artists/test_artist').set({name:'테스트 LUMI',type:'group',active:true});
 for(const [c,name]of [[a,'테스터A'],[b,'테스터B']]){
  const credential=await createUserWithEmailAndPassword(c.auth,`${name==='테스터A'?'a':'b'}${stamp}@example.com`,'testpass123!');
  await c.call('completeSignup',{nickname:name,terms:true,privacy:true});
 }
 aid=a.auth.currentUser.uid;bid=b.auth.currentUser.uid;
 await db.doc('schedules/test_schedule').set({artistId:'test_artist',title:'시연 일정',startAt:Date.now()+86400000,type:'방송',status:'예정',version:1});
}, {timeout:120000});
after(async()=>{await Promise.all(clients.map(c=>deleteApp(c.app)))});
test('guest may browse but cannot write or access private records',async()=>{
 assert.ok((await guest.call('artists')).items.some(x=>x.id==='test_artist'));
 await assert.rejects(guest.call('records'),e=>e.code==='functions/unauthenticated');
 await assert.rejects(guest.call('savePost',{}),e=>e.code==='functions/unauthenticated');
});
test('signup completion gates protected calls and never grants admin',async()=>{
 const c=client('incomplete-'+stamp);await createUserWithEmailAndPassword(c.auth,`incomplete${stamp}@example.com`,'testpass123!');
 await assert.rejects(c.call('me'),e=>e.code==='functions/permission-denied');
 await assert.rejects(c.call('completeSignup',{nickname:'x',terms:true,privacy:true}));
 await assert.rejects(c.call('completeSignup',{nickname:'테스트',terms:false,privacy:true}));
 await c.call('completeSignup',{nickname:'테스트',terms:true,privacy:true,admin:true});
 assert.equal((await c.call('me')).admin,undefined);
});
test('private record writes are validated and idempotent',async()=>{
 const fields={title:'첫 번째 공연',body:'잊지 못할 밤',date:'2026-10-20',category:'공연',artistId:'test_artist',media:[],requestKey:'record-'+stamp};
 record=(await a.call('saveRecord',fields)).id;
 assert.equal((await a.call('saveRecord',fields)).id,record);
 assert.equal((await a.call('record',{id:record})).title,fields.title);
 await assert.rejects(b.call('record',{id:record}),e=>e.code==='functions/not-found');
 await assert.rejects(a.call('saveRecord',{...fields,requestKey:'bad-'+stamp,title:'x'}));
 await assert.rejects(a.call('saveRecord',{...fields,requestKey:'date-'+stamp,date:'2026-02-30'}));
});
test('version conflicts preserve server data; other users cannot update or delete',async()=>{
 await assert.rejects(b.call('deleteRecord',{id:record,expectedVersion:1}),e=>e.code==='functions/not-found');
 await a.call('saveRecord',{id:record,expectedVersion:1,title:'수정한 기록',body:'수정 본문',date:'2026-10-20',category:'일상',artistId:'test_artist',media:[]});
 await assert.rejects(a.call('saveRecord',{id:record,expectedVersion:1,title:'오래된 수정',body:'덮어쓰면 안 됨',date:'2026-10-20',category:'일상',artistId:'test_artist',media:[]}),e=>e.code==='functions/aborted');
 assert.equal((await a.call('record',{id:record})).title,'수정한 기록');
});
test('direct Firestore access is denied even for signed-in users',async()=>{
 await assert.rejects(getDoc(doc(b.firestore,'records',record)),e=>e.code==='permission-denied');
 await assert.rejects(setDoc(doc(a.firestore,'users',aid),{admin:true}),e=>e.code==='permission-denied');
});
test('post is public, like is idempotent, comments require ownership to delete',async()=>{
 post=(await a.call('savePost',{title:'공연 이야기',body:'모두 즐거웠어요',category:'공연 후기',artistId:'test_artist',requestKey:'post-'+stamp,media:[]})).id;
 assert.equal((await guest.call('post',{id:post})).title,'공연 이야기');
 await b.call('like',{id:post,enabled:true});await b.call('like',{id:post,enabled:true});
 assert.equal((await a.call('post',{id:post})).likeCount,1);
 comment=(await b.call('comment',{id:post,body:'저도요!',requestKey:'comment-'+stamp})).id;
 assert.equal((await b.call('comment',{id:post,body:'저도요!',requestKey:'comment-'+stamp})).id,comment);
 await assert.rejects(a.call('deleteComment',{postId:post,id:comment}),e=>e.code==='functions/not-found');
 assert.equal((await a.call('post',{id:post})).comments.items.length,1);
});
test('reports do not hide content and duplicate reports reuse the receipt',async()=>{
 const input={targetType:'post',targetId:post,reason:'기타',detail:'테스트 신고'};
 const one=await b.call('report',input),two=await b.call('report',input);
 assert.equal(one.id,two.id);assert.ok((await guest.call('post',{id:post})).id);
 await assert.rejects(a.call('report',input));
});
test('blocks restrict both directions; removing one side keeps the other block',async()=>{
 await b.call('block',{userId:aid,enabled:true});
 await assert.rejects(b.call('post',{id:post}),e=>e.code==='functions/not-found');
 await assert.rejects(b.call('like',{id:post,enabled:true}));
 assert.ok(!(await b.call('posts')).items.some(x=>x.id===post));
 await a.call('block',{userId:bid,enabled:true});await b.call('block',{userId:aid,enabled:false});
 await assert.rejects(b.call('post',{id:post}));
 await a.call('block',{userId:bid,enabled:false});assert.ok((await b.call('post',{id:post})).id);
});
test('schedule reminder enforces state, 30 minute cutoff and version',async()=>{
 await a.call('reminder',{id:'test_schedule',enabled:true,expectedVersion:1});assert.equal((await a.call('schedule',{id:'test_schedule'})).reminded,true);
 await assert.rejects(a.call('reminder',{id:'test_schedule',enabled:true,expectedVersion:0}),e=>e.code==='functions/aborted');
 await db.doc('schedules/test_schedule').update({status:'취소',version:2});
 await assert.rejects(a.call('reminder',{id:'test_schedule',enabled:true,expectedVersion:2}));
 await a.call('reminder',{id:'test_schedule',enabled:false,expectedVersion:2});
 await db.doc('schedules/test_schedule').update({status:'예정',startAt:Date.now()+1000,version:3});
 await assert.rejects(a.call('reminder',{id:'test_schedule',enabled:true,expectedVersion:3}));
});
test('notification preferences and receipts belong to the recipient',async()=>{
 assert.deepEqual(await a.call('preferences'),{schedule:true,comment:true,like:false,memory:false});
 await a.call('preferences',{values:{memory:true,comment:false}});
 assert.equal((await a.call('preferences')).memory,true);
 const n=(await a.call('notifications')).items;assert.ok(n.length>=1);
 await assert.rejects(b.call('readNotification',{id:n[0].id}),e=>e.code==='functions/not-found');
 await a.call('readNotification',{id:n[0].id});
});
test('Storage rejects cross-account writes, unsupported types, and oversized photos',async()=>{
 const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=','base64');
 await uploadBytes(ref(a.storage,`uploads/${aid}/test.png`),png,{contentType:'image/png',customMetadata:{purpose:'record'}});
 await assert.rejects(uploadBytes(ref(b.storage,`uploads/${aid}/intruder.png`),png,{contentType:'image/png',customMetadata:{purpose:'record'}}),e=>e.code==='storage/unauthorized');
 await assert.rejects(uploadBytes(ref(a.storage,`uploads/${aid}/bad.txt`),Buffer.from('bad'),{contentType:'text/plain'}),e=>e.code==='storage/unauthorized');
 await assert.rejects(uploadBytes(ref(a.storage,`uploads/${aid}/large.png`),new Uint8Array(10*1024*1024+1),{contentType:'image/png',customMetadata:{purpose:'record'}}),e=>e.code==='storage/unauthorized');
 await assert.rejects(getBytes(ref(b.storage,`uploads/${aid}/test.png`)),e=>e.code==='storage/unauthorized');
 await assert.rejects(b.call('saveRecord',{title:'다른 사진',body:'거절',date:'2026-10-20',category:'일상',artistId:'test_artist',media:[`uploads/${aid}/test.png`],requestKey:'stolen-'+stamp}));
 const photoRecord=(await a.call('saveRecord',{title:'내 사진 기록',body:'비공개 사진',date:'2026-10-20',category:'일상',artistId:'test_artist',media:[`uploads/${aid}/test.png`],requestKey:'photo-'+stamp})).id;
 assert.ok((await a.call('media',{kind:'record',id:photoRecord})).urls[0].startsWith('data:image/png;base64,'));
 await assert.rejects(b.call('media',{kind:'record',id:photoRecord}),e=>e.code==='functions/not-found');
 await assert.rejects(guest.call('media',{kind:'record',id:photoRecord}),e=>e.code==='functions/unauthenticated');
});
test('ordinary members cannot invoke schedule or moderation administration',async()=>{
 await assert.rejects(a.call('adminSchedule',{id:'forbidden'}),e=>e.code==='functions/permission-denied');
 await assert.rejects(b.call('adminReports'),e=>e.code==='functions/permission-denied');
});
test('deleted posts cannot be reached through stale links',async()=>{
 await a.call('deletePost',{id:post,expectedVersion:1});
 await assert.rejects(guest.call('post',{id:post}),e=>e.code==='functions/not-found');
});
test('account deletion blocks access and removes private data',async()=>{
 await b.call('deleteAccount',{confirmation:true});
 const u=await adminAuth().getUser(bid).catch(e=>null);assert.ok(!u||u.disabled);
 await assert.rejects(b.call('me'));
 assert.equal((await db.doc(`users/${bid}`).get()).exists,false);
});
