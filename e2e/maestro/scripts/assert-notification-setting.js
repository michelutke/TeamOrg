// Asserts a boolean notification setting of the coach for the team through the API.
// Env: SETTING_NAME (e.g. eventsNew), EXPECTED_VALUE ("true" or "false").
function check(condition, message) {
  if (!condition) throw new Error(message);
}
var response = http.get(API_URL + '/notifications/settings/' + TEAM_ID, {
  headers: { Authorization: 'Bearer ' + COACH_TOKEN }
});
var settings = JSON.parse(response.body);
check(String(settings[SETTING_NAME]) === EXPECTED_VALUE, 'expected ' + SETTING_NAME + '=' + EXPECTED_VALUE + ' but was ' + settings[SETTING_NAME]);
