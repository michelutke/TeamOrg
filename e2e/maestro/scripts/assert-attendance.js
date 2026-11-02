// Asserts the attendance status of a user for an event through the API.
// Env: USER_ID_TO_CHECK, EXPECTED_STATUS, optional EXPECTED_REASON, optional EVENT_ID_TO_CHECK (default SINGLE_EVENT_ID),
// optional WAIT_SECONDS (poll until the status matches, default: check once).
function check(condition, message) {
  if (!condition) throw new Error(message);
}
var eventId = (typeof EVENT_ID_TO_CHECK !== 'undefined' && EVENT_ID_TO_CHECK !== '') ? EVENT_ID_TO_CHECK : SINGLE_EVENT_ID;
var waitMillis = (typeof WAIT_SECONDS !== 'undefined' && WAIT_SECONDS !== '') ? Number(WAIT_SECONDS) * 1000 : 0;
var deadline = Date.now() + waitMillis;
var row;
do {
  var response = http.get(API_URL + '/events/' + eventId + '/attendance', {
    headers: { Authorization: 'Bearer ' + COACH_TOKEN }
  });
  row = JSON.parse(response.body).filter(function (r) { return r.userId === USER_ID_TO_CHECK; })[0];
} while ((row === undefined || row.status !== EXPECTED_STATUS) && Date.now() < deadline);
check(row !== undefined, 'no attendance row for ' + USER_ID_TO_CHECK);
check(row.status === EXPECTED_STATUS, 'expected status ' + EXPECTED_STATUS + ' but was ' + row.status);
if (typeof EXPECTED_REASON !== 'undefined' && EXPECTED_REASON !== '') {
  check(row.reason === EXPECTED_REASON, 'expected reason ' + EXPECTED_REASON + ' but was ' + row.reason);
}
