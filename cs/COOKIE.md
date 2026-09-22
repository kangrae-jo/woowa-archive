# Cookie

### Cookie란?

Cookie는 브라우저 등 클라이언트가 저장하고, 조건에 맞는 HTTP 요청에 다시 담아 보내는 이름=값 데이터다.
Cookie 자체가 HTTP를 상태 저장 프로토콜로 바꾸지는 않는다. 애플리케이션이 여러 요청을 연결할 단서를 제공한다.

### 동작

#### 1. Set-Cookie and Cookie

- 서버는 응답의 `Set-Cookie` 헤더로 브라우저에 Cookie 저장을 요청한다.

```http
Set-Cookie: sessionId=opaque-id; Path=/; Secure; HttpOnly; SameSite=Lax
```

- 브라우저는 전송 조건에 맞는 후속 요청의 `Cookie` 헤더에 이름과 값만 보낸다. `Path`, `Secure` 같은 속성은 보내지 않는다.

```http
Cookie: sessionId=opaque-id
```

#### 2. Scope and Lifetime

- `Domain`과 `Path`는 Cookie를 전송할 대상의 범위를 정한다.
- `Max-Age`는 남은 수명을 초 단위로, `Expires`는 만료 시각으로 지정한다. 둘 다 있으면 `Max-Age`가 우선한다.
- 두 만료 속성이 없으면 브라우저 세션 동안 유지되는 Session Cookie가 된다.

#### 3. Cookie and Session

- 로그인 세션에 사용하는 Cookie에는 보통 세션 데이터가 아니라 서버에서 세션을 찾을 ID를 넣는다.
- 실제 로그인 상태나 장바구니 데이터는 서버의 Session에 둘 수 있다. Cookie와 Session은 같은 개념이 아니다.

#### 4. Security Attributes

- `Secure`: 일반적으로 HTTPS 요청에만 Cookie를 전송한다.
- `HttpOnly`: JavaScript에서 Cookie 값에 접근하지 못하게 한다.
- `SameSite`: 사이트 간 요청에서 Cookie를 보낼지 제한해 CSRF 위험을 줄이는 데 도움을 준다.
- Cookie 값은 클라이언트가 볼 수 있고 바꿀 수도 있으므로 서버는 값을 신뢰하지 않아야 한다.

### 키워드

- Session Cookie, Domain, Path, CSRF, XSS, Session Fixation
