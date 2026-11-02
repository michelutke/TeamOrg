// Asserts the attendance status of a user for the single event through the API.
// Env: USER_ID_TO_CHECK, EXPECTED_STATUS, optional EXPECTED_REASON.
var response = http.get(API_URL + '/events/' + SINGLE_EVENT_ID + '/attendance', {
  headers: { Authorization: 'Bearer ' + COACH_TOKEN }
});
var rows = JSON.parse(response.body);
var row = rows.filter(function (r) { return r.userId === USER_ID_TO_CHECK; })[0];
assertTrue(row !== undefined, 'no attendance row for ' + USER_ID_TO_CHECK);
assertTrue(row.status === EXPECTED_STATUS, 'expected status ' + EXPECTED_STATUS + ' but was ' + row.status);
if (typeof EXPECTED_REASON !== 'undefined' && EXPECTED_REASON !== '') {
  assertTrue(row.reason === EXPECTED_REASON, 'expected reason ' + EXPECTED_REASON + ' but was ' + row.reason);
}
