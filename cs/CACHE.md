# Cache

### Cache란?

Cache는 이전 요청의 결과를 저장하고 이후 요청에서 재사용하여 응답 시간을 줄이는 방식이다.
HTTP Cache는 재사용 가능한 응답 메시지를 저장해 응답 지연, 네트워크 전송량, 원본 서버 부하를 줄일 수 있다.

### HTTP Cache의 동작

아래 예시의 Cache는 CDN의 Shared Cache다. 브라우저에도 사용자별 Private Cache가 별도로 있을 수 있다.

#### 1. Cache Miss and Cache Hit

- Cache Miss: CDN에 저장된 응답이 없어 원본 서버에 요청한다. Client는 원본 서버의 새 본문을 받는다.
- Cache Hit: 저장된 응답이 유효하면 원본 서버에 요청하지 않고 CDN의 본문을 Client에 반환한다.
- 저장된 응답이 만료되면 일반적으로 원본 서버에 변경 여부를 확인해 재사용하거나 새 응답으로 교체한다.

#### 2. Freshness

- `Cache-Control: max-age=120`은 응답의 신선도 유효 기간을 120초로 지정한다. 다른 제약이 없다면 응답 나이가 120초 미만일 때 재검증 없이 재사용할 수 있다.
- `Expires`는 절대 만료 시각을 나타낸다. `max-age`가 함께 있으면 `max-age`가 우선한다.

#### 3. Revalidation

- 만료된 응답의 `ETag`는 `If-None-Match`로, `Last-Modified`는 `If-Modified-Since`로 원본 서버에 전달할 수 있다.
- 두 조건부 헤더를 함께 보내면 서버는 `If-None-Match`를 우선하고 `If-Modified-Since`는 무시한다.

```http
GET /app.js HTTP/1.1
Host: example.com
If-None-Match: "js-v7"
If-Modified-Since: Tue, 22 Feb 2022 09:30:00 GMT
```

- 변경되지 않았다면 원본 서버는 본문 없는 `304 Not Modified`를 반환하고, CDN은 저장된 본문을 Client에 전달한다.
- 변경되었다면 원본 서버는 새 본문이 있는 `200 OK`를 반환한다.
- 조건부 요청 헤더가 없다면 이 재검증 방식으로 `304`를 받을 수 없다.

#### 4. Cache-Control Directives

- `Cache-Control: no-cache`: 저장은 가능하지만 재사용 전에 검증해야 한다.
- `Cache-Control: no-store`: 응답을 Cache에 저장하지 않는다.
- `Cache-Control: private`: 브라우저 같은 Private Cache만 저장할 수 있고 CDN 같은 Shared Cache는 저장할 수 없다.

#### 5. Cache Busting

- 파일이 바뀔 때 `/app.a1.js`를 `/app.b2.js`처럼 새 URL로 바꾸면 기존 Cache 항목과 일치하지 않아 새 파일을 요청한다.
- `/index.html`도 갱신하거나 재검증해야 Client가 변경된 JS URL을 알 수 있다.

### 키워드

- Cache Key, Vary, Age, s-maxage, Cache Invalidation, stale-while-revalidate
