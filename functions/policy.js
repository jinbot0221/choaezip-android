'use strict';
const SEOUL_OFFSET = 9 * 60 * 60 * 1000;
function koreaDay(now) { return new Date(now + SEOUL_OFFSET).toISOString().slice(0, 10); }
function reminderEligible(schedule, now) {
  return schedule.status === '예정' && Number.isFinite(schedule.startAt) && schedule.startAt > now + 30 * 60 * 1000;
}
function communityQuietHours(now) {
  const hour = new Date(now + SEOUL_OFFSET).getUTCHours();
  return hour >= 22 || hour < 8;
}
function anniversaryDates(now) {
  const day = koreaDay(now), previousYear = Number(day.slice(0,4)) - 1;
  // Feb 29 is observed on Feb 28 of the following non-leap year.
  const dates = [`${previousYear}${day.slice(4)}`];
  if (day.endsWith('-02-28') && new Date(Date.UTC(previousYear, 1, 29)).getUTCMonth() === 1) dates.push(`${previousYear}-02-29`);
  return dates;
}
function validMediaPath(uid, path) {
  return typeof path === 'string' && path.startsWith(`uploads/${uid}/`) && /^uploads\/[^/]+\/[A-Za-z0-9_.-]+$/.test(path);
}
module.exports = {koreaDay,reminderEligible,communityQuietHours,anniversaryDates,validMediaPath};
