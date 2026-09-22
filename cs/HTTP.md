# HTTP (Hypertext Transfer Protocol)

### HTTP란?

HTTP는 네트워크 기반 시스템에서 리소스의 표현을 요청과 응답 메시지로 교환하기 위한 무상태 애플리케이션 계층 프로토콜이다.

HTTP/1.1 메시지는 시작 라인(Start Line), 헤더 필드, 헤더와 본문을 구분하는 빈 줄, 선택적인 메시지 본문(Message Body)으로 구성된다.

```http
POST /users HTTP/1.1
Host: api.example.com
Content-Type: application/json
Content-Length: 14

{"name":"Kim"}
```

### 특성

#### 1. Application Layer

- OSI 7가지 계층 중 최상단으로, Process가 네트워크로 통신하기 위한 syntax, semantics, rules를 정의한다.
- 웹 브라우저 등의 애플리케이션은 HTTP와 같은 Application Layer Protocol을 사용해 통신한다.
- HTTP, SMTP 등이 있다.

#### 2. Client-Server Model

- Client는 요청을 전송한다.
- Server는 요청을 받아 처리하고 응답을 전송한다.
- Client와 Server는 고정된 기기 종류가 아니라 특정 연결에서 수행하는 역할이다.
- Proxy, Gateway 등의 중개자가 요청과 응답 사이에 존재할 수 있다.

#### 3. Stateless Protocol

- Server는 이전 요청의 순서를 가정하지 않고 요청 내용에 따라 처리한다.
  - 각 요청 메시지의 의미는 다른 요청과 독립적으로 해석된다.
  - 필요한 문맥은 현재 요청에 포함되어야 한다.
- Cookie나 Server Session을 활용하여 Application 수준의 상태를 구성할 수 있다.


### 키워드

- OSI 7계층, [Proxy](./PROXY.md), Gateway, [Cookie](./COOKIE.md), [Session](./SESSION.md)
