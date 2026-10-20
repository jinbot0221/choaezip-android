const {test}=require('node:test');
const assert=require('node:assert/strict');
const {koreaDay,reminderEligible,communityQuietHours,anniversaryDates,validMediaPath}=require('../policy');
const at=s=>Date.parse(s);
test('Seoul dates cross UTC midnight correctly',()=>{
 assert.equal(koreaDay(at('2026-10-20T14:59:59Z')),'2026-10-20');
 assert.equal(koreaDay(at('2026-10-20T15:00:00Z')),'2026-10-21');
});
test('30 minute reminder boundary excludes late requests and canceled schedules',()=>{
 const now=at('2026-10-20T00:00:00Z');
 assert.equal(reminderEligible({status:'예정',startAt:now+1800001},now),true);
 assert.equal(reminderEligible({status:'예정',startAt:now+1800000},now),false);
 assert.equal(reminderEligible({status:'취소',startAt:now+86400000},now),false);
 assert.equal(reminderEligible({status:'예정',startAt:now-1},now),false);
});
test('community push suppression includes 22:00 and excludes 08:00 in Seoul',()=>{
 assert.equal(communityQuietHours(at('2026-10-20T21:59:59+09:00')),false);
 assert.equal(communityQuietHours(at('2026-10-20T22:00:00+09:00')),true);
 assert.equal(communityQuietHours(at('2026-10-21T07:59:59+09:00')),true);
 assert.equal(communityQuietHours(at('2026-10-21T08:00:00+09:00')),false);
});
test('Feb 29 memories occur on Feb 28 in a following non-leap year',()=>{
 assert.deepEqual(anniversaryDates(at('2025-02-28T20:00:00+09:00')),['2024-02-28','2024-02-29']);
 assert.deepEqual(anniversaryDates(at('2026-02-28T20:00:00+09:00')),['2025-02-28']);
 assert.deepEqual(anniversaryDates(at('2026-01-01T20:00:00+09:00')),['2025-01-01']);
});
test('media references cannot escape the uploader namespace',()=>{
 assert.equal(validMediaPath('alice','uploads/alice/photo-1.png'),true);
 for(const path of ['uploads/bob/photo.png','uploads/alice/../bob/photo.png','uploads/alice/','uploads/alice/x/y','https://example.com/photo.png',null]) assert.equal(validMediaPath('alice',path),false);
});
