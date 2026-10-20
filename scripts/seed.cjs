// Only an explicitly selected demo emulator may be seeded.
process.env.FIRESTORE_EMULATOR_HOST ||= '127.0.0.1:8080';
process.env.FIREBASE_AUTH_EMULATOR_HOST ||= '127.0.0.1:9099';
process.env.FIREBASE_STORAGE_EMULATOR_HOST ||= '127.0.0.1:9199';
const {initializeApp}=require('../functions/node_modules/firebase-admin/app');
const {getFirestore}=require('../functions/node_modules/firebase-admin/firestore');
initializeApp({projectId:'demo-choaezip',storageBucket:'demo-choaezip.appspot.com'});
const db=getFirestore();
async function main(){
 const batch=db.batch(), now=Date.now();
 for(const [id,a] of Object.entries({lumi:{name:'LUMI · 루미',type:'group'},lumi_yuna:{name:'유나 · LUMI',type:'member',parentId:'lumi'},lumi_haru:{name:'하루 · LUMI',type:'member',parentId:'lumi'}})) batch.set(db.doc(`artists/${id}`),{...a,active:true});
 const entries=[['music','뮤직 스테이지 사전녹화','방송',1,'상암 스튜디오'],['starlight','STARLIGHT 컴백 라이브','컴백',7,'공식 온라인 채널'],['concert','LUMI 팬 콘서트','공연',12,'서울 올림픽홀'],['ticket','팬 콘서트 일반 예매','티켓',3,'공식 예매처']];
 for(const [id,title,type,offset,location]of entries){
  const date=new Date(now+offset*86400000);date.setUTCHours(9,0,0,0);
  batch.set(db.doc(`schedules/${id}`),{title,type,location,artistId:'lumi',startAt:date.getTime(),status:'예정',version:1,body:'가상 아티스트 LUMI의 개발용 예시 일정입니다. 실제 아티스트의 일정이 아닙니다.',sourceName:'개발용 예시',sourceUrl:'https://firebase.google.com/',updatedAt:now});
 }
 await batch.commit(); console.log('Seeded 3 fictional artists and 4 schedules in demo-choaezip emulator.');
}
main().catch(e=>{console.error(e.message);process.exitCode=1});
