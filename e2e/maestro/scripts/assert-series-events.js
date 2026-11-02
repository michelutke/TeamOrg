// Asserts through the API that the coach's series (title SERIES_TITLE_TO_CHECK) materialised weekly occurrences.
var response = http.get(API_URL + '/teams/' + TEAM_ID + '/events', {
  headers: { Authorization: 'Bearer ' + COACH_TOKEN }
});
var events = JSON.parse(response.body).filter(function (e) {
  var event = e.event || e;
  return event.title === SERIES_TITLE_TO_CHECK;
}).map(function (e) { return e.event || e; });
assertTrue(events.length >= 2, 'expected at least 2 occurrences of ' + SERIES_TITLE_TO_CHECK + ', got ' + events.length);
var seriesIds = {};
events.forEach(function (e) { seriesIds[e.seriesId] = true; });
assertTrue(Object.keys(seriesIds).length === 1 && events[0].seriesId !== null, 'occurrences must share one series id');
var starts = events.map(function (e) { return new Date(e.startAt).getTime(); }).sort(function (a, b) { return a - b; });
for (var i = 1; i < starts.length; i++) {
  // Rounded to whole days so a DST switch between occurrences does not fail the check.
  assertTrue(Math.round((starts[i] - starts[i - 1]) / (24 * 3600 * 1000)) === 7, 'occurrences must be one week apart');
}
output.occurrences = events.length;
