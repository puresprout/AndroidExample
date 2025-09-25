## 유의사항

### Git push 오류(RPC failed)와 http.postBuffer 해결 방법
push하다가 다음과 같은 오류가 나면 용량 문제 때문이다.
```text
error: RPC failed; HTTP 400 curl 22 The requested URL returned error: 400
send-pack: unexpected disconnect while reading sideband packet
fatal: the remote end hung up unexpectedly
```

다음 명령어로 http.postBuffer 설정값이 어떻게 설정되어 있는지 먼저 확인하다.
```text
git config --show-origin http.postBuffer
```

아무 값도 안 나오면 기본값(최근 Git은 1MB)을 내부적으로 사용한다.
만일 push하려는 파일(이미지 파일등)의 용량이 1MB를 넘으면 위 기본값 때문에 실패하는 것이다.

다음 명령어로 http.postBuffer 값을 10MB로 늘려준다.
```text
git config http.postBuffer 10485760
```

다시 push 시도하면 한번더 인증 절차를 거치고 성공할 것이다.

하지만 가급적 파일(이미지 파일등) 용량을 줄이는 것이 좋다.