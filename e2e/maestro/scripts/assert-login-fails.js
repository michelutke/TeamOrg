// Asserts through the API that the deleted account can no longer sign in. Env: DELETED_EMAIL, PASSWORD.
function check(condition, message) {
  if (!condition) throw new Error(message);
}
var response = http.post(API_URL + '/auth/login', {
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: DELETED_EMAIL, password: PASSWORD })
});
check(response.status === 401, 'expected HTTP 401 for deleted account ' + DELETED_EMAIL + ' but was ' + response.status);
