// Asserts through the API the unread notification count of the coach. Env: EXPECTED_COUNT.
function check(condition, message) {
  if (!condition) throw new Error(message);
}
var response = http.get(API_URL + '/notifications/unread-count', {
  headers: { Authorization: 'Bearer ' + COACH_TOKEN }
});
var body = JSON.parse(response.body);
var count = body.count !== undefined ? body.count : body.unreadCount;
check(String(count) === String(EXPECTED_COUNT), 'expected unread count ' + EXPECTED_COUNT + ' but was ' + count);
