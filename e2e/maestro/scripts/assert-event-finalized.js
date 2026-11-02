// Asserts through the API that the past event reports check-in status "done".
// Env: EVENT_ID_TO_CHECK.
function check(condition, message) {
  if (!condition) throw new Error(message);
}
var response = http.get(API_URL + '/events/' + EVENT_ID_TO_CHECK, {
  headers: { Authorization: 'Bearer ' + COACH_TOKEN }
});
var body = JSON.parse(response.body);
var event = body.event || body;
check(event.checkInStatus === 'done', 'expected checkInStatus done but was ' + event.checkInStatus);
