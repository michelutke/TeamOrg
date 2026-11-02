// Sets the attendance of every member of the seeded team for an event as the coach, so that finalizing is not
// blocked by members who joined in earlier flows. Env: EVENT_ID_TO_SET, STATUS_TO_SET (confirmed|declined).
function check(condition, message) {
  if (!condition) throw new Error(message);
}
var headers = { Authorization: 'Bearer ' + COACH_TOKEN, 'Content-Type': 'application/json' };
var members = JSON.parse(http.get(API_URL + '/teams/' + TEAM_ID + '/members', { headers: headers }).body);
members.forEach(function (member) {
  var response = http.put(API_URL + '/events/' + EVENT_ID_TO_SET + '/attendance/' + member.userId, {
    headers: headers,
    body: JSON.stringify({ status: STATUS_TO_SET, unexcused: false })
  });
  check(response.ok, 'set attendance failed for ' + member.userId + ' with HTTP ' + response.status + ': ' + response.body);
});
