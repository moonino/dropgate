import { generateKeyPairSync, randomUUID, sign } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const ISSUER = 'dropgate-auth';
const KEY_ID = 'load-test-key';
const RSA_MODULUS_BITS = 2048;
const ACCESS_TOKEN_TTL_SECONDS = 15 * 60;
const REPEATING_USER_INTERVAL = 8;
const REQUESTS_PER_REPEATING_USER = 3;

const [outputDir, requestCountArgument] = process.argv.slice(2);
const requestCount = Number.parseInt(requestCountArgument, 10);
if (!outputDir || !Number.isInteger(requestCount) || requestCount < 1) {
  console.error('사용법: node issue-tokens.mjs <출력 디렉터리> <구매 요청 수>');
  process.exit(1);
}

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: RSA_MODULUS_BITS });

const encodeSegment = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');

const issueAccessToken = (role, issuedAtSeconds) => {
  const header = encodeSegment({ alg: 'RS256', kid: KEY_ID });
  const claims = encodeSegment({
    sub: randomUUID(),
    iss: ISSUER,
    jti: randomUUID(),
    role,
    iat: issuedAtSeconds,
    exp: issuedAtSeconds + ACCESS_TOKEN_TTL_SECONDS,
  });
  const signature = sign('RSA-SHA256', Buffer.from(`${header}.${claims}`), privateKey).toString('base64url');
  return `${header}.${claims}.${signature}`;
};

const requestsOfUser = (userIndex) =>
  userIndex % REPEATING_USER_INTERVAL === 0 ? REQUESTS_PER_REPEATING_USER : 1;

const issuePurchaseRequestTokens = (requestCount, issuedAtSeconds) => {
  const requestTokens = [];
  let userCount = 0;
  let fullyRepeatingUserCount = 0;
  for (let userIndex = 0; requestTokens.length < requestCount; userIndex += 1) {
    const userToken = issueAccessToken('USER', issuedAtSeconds);
    const repeats = Math.min(requestsOfUser(userIndex), requestCount - requestTokens.length);
    requestTokens.push(...Array(repeats).fill(userToken));
    userCount += 1;
    if (repeats === REQUESTS_PER_REPEATING_USER) {
      fullyRepeatingUserCount += 1;
    }
  }
  return { requestTokens, userCount, fullyRepeatingUserCount };
};

const issuedAtSeconds = Math.floor(Date.now() / 1000);
const jwkSet = { keys: [{ ...publicKey.export({ format: 'jwk' }), use: 'sig', alg: 'RS256', kid: KEY_ID }] };
const { requestTokens, userCount, fullyRepeatingUserCount } = issuePurchaseRequestTokens(
  requestCount,
  issuedAtSeconds,
);

mkdirSync(outputDir, { recursive: true });
writeFileSync(join(outputDir, 'jwks.json'), JSON.stringify(jwkSet));
writeFileSync(join(outputDir, 'purchase-request-tokens.json'), JSON.stringify(requestTokens));
writeFileSync(join(outputDir, 'admin-token.txt'), issueAccessToken('ADMIN', issuedAtSeconds));
console.log(
  `구매 요청 토큰 ${requestTokens.length}개(사용자 ${userCount}명, 그중 ${fullyRepeatingUserCount}명은 같은 토큰으로 연달아 ${REQUESTS_PER_REPEATING_USER}번)와 ADMIN 토큰 1개, JWKS를 ${outputDir} 에 썼습니다`,
);
