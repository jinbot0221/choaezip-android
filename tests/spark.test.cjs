'use strict';
const {test,before,after}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {initializeApp,deleteApp}=require('firebase/app');
const {getAuth,connectAuthEmulator,createUserWithEmailAndPassword,signInWithEmailAndPassword,signOut}=require('firebase/auth');
const {getFirestore,connectFirestoreEmulator,doc,getDoc,setDoc,updateDoc,deleteDoc,writeBatch,serverTimestamp,collection,collectionGroup,query,where,orderBy,limit,startAfter,getDocs,runTransaction,getCountFromServer}=require('firebase/firestore');
let env;
const projectId='demo-choaezip', apps=[];
const a=()=>env.authenticatedContext('alice').firestore();
const b=()=>env.authenticatedContext('bob').firestore();
const guest=()=>env.unauthenticatedContext().firestore();
async function signup(db,uid,nickname='테스터') {
 const batch=writeBatch(db);
 batch.set(doc(db,'users',uid),{nickname,bio:'',completed:true,termsVersion:'1.0',privacyVersion:'1.0',createdAt:serverTimestamp(),consentedAt:serverTimestamp()});
 batch.set(doc(db,'publicProfiles',uid),{nickname,bio:''});
 return batch.commit();
}
function content(uid,record=false){return {ownerId:uid,title:'테스트 글',body:'첫 줄\n둘째 줄',artistId:'lumi',category:record?'일상':'자유 이야기',media:[],version:1,createdAt:serverTimestamp(),updatedAt:serverTimestamp(),...(record?{date:'2026-10-21'}:{hidden:false})};}
before(async()=>{
 env=await initializeTestEnvironment({projectId,firestore:{rules:fs.readFileSync('firestore.rules','utf8'),host:'127.0.0.1',port:8080}});
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async ctx=>{
  await setDoc(doc(ctx.firestore(),'artists','lumi'),{name:'LUMI',active:true});
  await setDoc(doc(ctx.firestore(),'schedules','test'),{artistId:'lumi',startAt:Date.now(),title:'일정'});
 });
 await signup(a(),'alice');await signup(b(),'bob');
});
after(async()=>{await Promise.all(apps.map(deleteApp));await env.cleanup();});
test('guest can browse schedules/artists, not private profiles or writes',async()=>{
 assert.equal((await getDocs(query(collection(guest(),'artists'),where('active','==',true)))).size,1);
 await assertSucceeds(getDocs(collection(guest(),'schedules')));
 await assertFails(getDoc(doc(guest(),'users','alice')));
 await assertFails(setDoc(doc(guest(),'posts','guest'),content('guest')));
 await assertFails(setDoc(doc(a(),'artists','evil'),{active:true}));
});
test('signup is atomic, enforces consent fields and prevents privilege escalation',async()=>{
 const c=env.authenticatedContext('incomplete').firestore();
 await assertSucceeds(getDoc(doc(c,'users','incomplete')));
 await assertFails(setDoc(doc(c,'users','incomplete'),{nickname:'테스터',bio:'',completed:true}));
 await assertFails(setDoc(doc(c,'posts','incomplete'),content('incomplete')));
 await assertSucceeds(signup(c,'incomplete'));
 await assertFails(updateDoc(doc(c,'users','incomplete'),{admin:true}));
 await assertFails(updateDoc(doc(c,'users','incomplete'),{completed:false}));
 await assertFails(updateDoc(doc(a(),'users','bob'),{nickname:'침입자'}));
 await assertFails(setDoc(doc(c,'publicProfiles','incomplete'),{nickname:'테스터',bio:'',email:'private@example.com'}));
 await assertFails(updateDoc(doc(c,'users','incomplete'),{nickname:'불일치'}));
 const batch=writeBatch(c);batch.update(doc(c,'users','incomplete'),{nickname:'변경닉네임'});batch.set(doc(c,'publicProfiles','incomplete'),{nickname:'변경닉네임',bio:''});await assertSucceeds(batch.commit());
 await assertSucceeds(getDoc(doc(guest(),'publicProfiles','incomplete')));
});
test('real Auth emulator signup and fresh-client login retain Firestore profile and post',async()=>{
 const make=name=>{
  const app=initializeApp({projectId,apiKey:'demo-api-key',appId:'demo'},name);apps.push(app);
  const auth=getAuth(app);connectAuthEmulator(auth,'http://127.0.0.1:9099',{disableWarnings:true});
  const db=getFirestore(app);connectFirestoreEmulator(db,'127.0.0.1',8080);return {auth,db};
 };
 const first=make('first');const email=`spark-${Date.now()}@example.com`,password='password123!';
 const account=await createUserWithEmailAndPassword(first.auth,email,password);const uid=account.user.uid;
 await signup(first.db,uid,'실제가입');
 const ref=doc(first.db,'posts','persisted');
 await runTransaction(first.db,async tx=>{const prior=await tx.get(ref);if(!prior.exists())tx.set(ref,content(uid));});
 await signOut(first.auth);
 const returning=make('returning');await signInWithEmailAndPassword(returning.auth,email,password);
 assert.equal((await getDoc(doc(returning.db,'users',uid))).data().nickname,'실제가입');
 assert.equal((await getDoc(doc(returning.db,'posts','persisted'))).data().body,'첫 줄\n둘째 줄');
 await assertFails(getDoc(doc(returning.db,'users','alice')));
});
test('post create, ownership, edit version, no media and server timestamps are enforced',async()=>{
 await assertSucceeds(setDoc(doc(a(),'posts','post'),content('alice')));
 await assertSucceeds(setDoc(doc(a(),'posts','general'),{...content('alice'),artistId:'general'}));
 await assertSucceeds(getDoc(doc(guest(),'posts','post')));
 await assertFails(setDoc(doc(a(),'posts','spoof'),content('bob')));
 await assertFails(setDoc(doc(a(),'posts','photo'),{...content('alice'),media:['uploads/alice/photo']}));
 await assertFails(setDoc(doc(a(),'posts','bad-counter'),{...content('alice'),likeCount:100}));
 await assertFails(setDoc(doc(a(),'posts','clock'),{...content('alice'),createdAt:new Date(0)}));
 await assertFails(updateDoc(doc(b(),'posts','post'),{body:'타인수정',updatedAt:serverTimestamp(),version:2}));
 await assertSucceeds(updateDoc(doc(a(),'posts','post'),{body:'수정 본문',updatedAt:serverTimestamp(),version:2}));
 await assertFails(updateDoc(doc(a(),'posts','post'),{body:'경합',updatedAt:serverTimestamp(),version:2}));
 await assertFails(updateDoc(doc(a(),'posts','post'),{ownerId:'bob',updatedAt:serverTimestamp(),version:3}));
});
test('records remain private and valid ordered queries work',async()=>{
 await assertSucceeds(setDoc(doc(a(),'records','private'),content('alice',true)));
 await assertFails(setDoc(doc(a(),'records','invalid-date'),{...content('alice',true),date:'2026-02-30'}));
 await assertSucceeds(getDoc(doc(a(),'records','private')));
 await assertFails(getDoc(doc(b(),'records','private')));
 await assertFails(getDoc(doc(guest(),'records','private')));
 await assertFails(getDocs(collection(a(),'records')));
 const q=query(collection(a(),'records'),where('ownerId','==','alice'),orderBy('date','desc'),orderBy('createdAt','desc'),orderBy('__name__','desc'));
 assert.equal((await getDocs(q)).size,1);
 await assertFails(updateDoc(doc(b(),'records','private'),{body:'침입',version:2,updatedAt:serverTimestamp()}));
});
test('public pagination, comments and aggregate likes work without Functions',async()=>{
 const q=query(collection(guest(),'posts'),where('hidden','==',false),orderBy('createdAt','desc'),orderBy('__name__','desc'),limit(1));
 const first=await getDocs(q);assert.equal(first.size,1);
 const next=await getDocs(query(q,startAfter(first.docs[0])));assert.equal(next.size,1);
 await assertSucceeds(setDoc(doc(b(),'posts/post/comments/comment'),{ownerId:'bob',body:'좋은 이야기예요',hidden:false,createdAt:serverTimestamp()}));
 await assertSucceeds(getDocs(query(collection(guest(),'posts/post/comments'),where('hidden','==',false),orderBy('createdAt','desc'))));
 await assertFails(deleteDoc(doc(a(),'posts/post/comments/comment')));
 await assertSucceeds(setDoc(doc(b(),'posts/post/likes/bob'),{ownerId:'bob',createdAt:serverTimestamp()}));
 assert.equal((await getCountFromServer(collection(guest(),'posts/post/likes'))).data().count,1);
 await assertFails(setDoc(doc(a(),'posts/post/likes/bob'),{ownerId:'alice',createdAt:serverTimestamp()}));
 await assertSucceeds(deleteDoc(doc(b(),'posts/post/likes/bob')));
});
test('blocks prevent reactions in either direction; block lists stay private',async()=>{
 await assertSucceeds(setDoc(doc(a(),'users/alice/blocks/bob'),{createdAt:serverTimestamp()}));
 await assertSucceeds(getDoc(doc(b(),'users/alice/blocks/bob')));
 await assertFails(getDocs(collection(b(),'users/alice/blocks')));
 await assertFails(setDoc(doc(b(),'posts/post/likes/bob'),{ownerId:'bob',createdAt:serverTimestamp()}));
 await assertFails(setDoc(doc(b(),'posts/post/comments/blocked'),{ownerId:'bob',body:'차단회피',hidden:false,createdAt:serverTimestamp()}));
 await assertSucceeds(deleteDoc(doc(a(),'users/alice/blocks/bob')));
});
test('reports cannot be self-approved and server queues remain disabled',async()=>{
 const report={reporterId:'bob',targetType:'post',targetId:'post',postId:'post',reason:'스팸',detail:'',status:'pending',createdAt:serverTimestamp()};
 await assertSucceeds(setDoc(doc(b(),'reports','report'),report));
 await assertFails(updateDoc(doc(b(),'reports','report'),{status:'reviewed'}));
 await assertFails(getDoc(doc(a(),'reports','report')));
 await assertFails(setDoc(doc(b(),'outbox','fake'),{recipient:'alice'}));
 await assertFails(setDoc(doc(b(),'users/bob/devices/token'),{token:'private'}));
});
test('restricted accounts cannot create content or forge moderation fields',async()=>{
 await env.withSecurityRulesDisabled(async ctx=>updateDoc(doc(ctx.firestore(),'users','bob'),{restricted:true}));
 await assertFails(setDoc(doc(b(),'posts','restricted'),content('bob')));
 await assertFails(setDoc(doc(b(),'posts/general/likes/bob'),{ownerId:'bob',createdAt:serverTimestamp()}));
 await assertFails(updateDoc(doc(b(),'users','bob'),{restricted:false}));
 await env.withSecurityRulesDisabled(async ctx=>updateDoc(doc(ctx.firestore(),'users','bob'),{restricted:false}));
});
test('hidden tombstones deny public children and owner cleanup group queries work',async()=>{
 await assertSucceeds(updateDoc(doc(a(),'posts','post'),{title:'삭제된 게시글',body:'삭제된 내용',hidden:true,version:3,updatedAt:serverTimestamp()}));
 await assertFails(getDoc(doc(guest(),'posts','post')));
 await assertFails(getDocs(query(collection(guest(),'posts/post/comments'),where('hidden','==',false))));
 const own=await getDocs(query(collectionGroup(b(),'comments'),where('ownerId','==','bob')));
 assert.equal(own.size,1);await assertSucceeds(deleteDoc(own.docs[0].ref));
 await assertFails(getDocs(collectionGroup(b(),'comments')));
 const alice=a();const batch=writeBatch(alice);batch.delete(doc(alice,'publicProfiles','alice'));batch.delete(doc(alice,'users','alice'));await assertSucceeds(batch.commit());
 await assertFails(setDoc(doc(a(),'posts','after-delete'),content('alice')));
});
