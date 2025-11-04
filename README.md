# Newsletter Service

Spring Boot 3 (Java 17) 기반 자가 호스팅 뉴스레터 서비스

---

## 🚨 중요: 데이터베이스 설정

**⚠️ Cloud Run에서 SQLite는 데이터가 휘발됩니다!**

- Cloud Run은 **stateless 컨테이너** 환경
- 컨테이너 재시작/재배포 시 **모든 구독자 데이터 삭제**
- **프로덕션에서는 반드시 PostgreSQL 사용** (Supabase 무료 추천)

---

## 📋 주요 기능

- **PostgreSQL** (프로덕션 권장) 또는 SQLite (로컬 테스트)
- Flyway 자동 마이그레이션
- 구독/확인/해제 API
- RSS 폴링 스케줄러 (15분마다)
- 이메일 발송 스케줄러 (5분마다)
- Dockerfile (Cloud Run 배포용)

---

## 🚀 빠른 시작

### 빌드 (Gradle)

```bash
./gradlew clean bootJar
```

### 로컬 실행

```bash
java -jar build/libs/newsletter-service-0.0.1-SNAPSHOT.jar
```

또는:

```bash
./gradlew bootRun
```

---

## 📡 API 엔드포인트

- `POST /api/subscribers?email=you@example.com` - 구독 신청
- `GET /api/subscribers/confirm?token=...` - 구독 확인
- `GET /api/subscribers/unsubscribe?token=...` - 구독 해제

---

## 🔧 설정

### 환경 변수

주요 설정은 `application.yml`에서 관리하거나 환경 변수로 오버라이드할 수 있습니다:

```bash
# SMTP 설정
SPRING_MAIL_HOST=smtp.sendgrid.net
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=apikey
SPRING_MAIL_PASSWORD=YOUR_SENDGRID_API_KEY

# 애플리케이션 설정
APP_MAIL_FROM=noreply@example.com
APP_SITE_URL=https://yourdomain.com
APP_API_BASE_URL=https://api.yourdomain.com
APP_RSS_URL=https://yourdomain.com/rss.xml

# 데이터베이스 (PostgreSQL 사용 시)
SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/dbname
SPRING_DATASOURCE_USERNAME=user
SPRING_DATASOURCE_PASSWORD=password
```

### 데이터베이스 커넥션 풀 최적화

**Supabase 무료 플랜은 최대 연결 수가 제한되어 있습니다** (보통 15-20개).

#### 현재 설정 (application.yml)

- `maximum-pool-size: 3` - 인스턴스당 최대 3개 커넥션
- `minimum-idle: 1` - 최소 유휴 커넥션 1개

---

## ☁️ Cloud Run 배포

### Docker 이미지 빌드 및 푸시

```bash
PROJECT_ID=your-gcp-project-id
REGION=asia-northeast3
SERVICE=newsletter-service

# 이미지 빌드
docker build -t gcr.io/$PROJECT_ID/$SERVICE:latest .

# GCR에 푸시
docker push gcr.io/$PROJECT_ID/$SERVICE:latest
```

### Cloud Run 배포

```bash
gcloud run deploy $SERVICE \
  --image gcr.io/$PROJECT_ID/$SERVICE:latest \
  --platform managed \
  --region $REGION \
  --allow-unauthenticated \
  --min-instances 0 \
  --max-instances 2 \
  --set-env-vars SPRING_MAIL_HOST=smtp.sendgrid.net \
  --set-env-vars SPRING_MAIL_PORT=587 \
  --set-env-vars SPRING_MAIL_USERNAME=apikey \
  --set-env-vars SPRING_MAIL_PASSWORD=YOUR_SENDGRID_API_KEY \
  --set-env-vars APP_MAIL_FROM=noreply@example.com \
  --set-env-vars APP_SITE_URL=https://yourdomain.com \
  --set-env-vars APP_API_BASE_URL=https://YOUR_RUN_URL \
  --set-env-vars APP_RSS_URL=https://yourdomain.com/rss.xml
```

**참고**: Gmail SMTP는 Cloud Run에서 차단/제약이 있을 수 있어 SendGrid/SES 권장

---

## 🔗 프론트엔드 연동

### Gatsby 예제

```jsx
<form
  className="newsletter__form"
  action="https://YOUR_RUN_URL/api/subscribers"
  method="post"
  name="newsletter"
>
  <input
    className="newsletter__email"
    type="email"
    name="email"
    placeholder="이메일을 입력하세요"
    required
  />
  <button className="newsletter__button" type="submit">
    구독하기
  </button>
</form>
```

---

## 🛠️ 기술 스택

- **Java 17**
- **Spring Boot 3.3.4**
- **Gradle** (Kotlin DSL)
- **Flyway** - 데이터베이스 마이그레이션
- **HikariCP** - 커넥션 풀
- **PostgreSQL** / SQLite
- **Rome** - RSS 파싱
- **Thymeleaf** - 이메일 템플릿

---

## 📝 License

MIT License

---

## 🤝 Contributing

이슈와 PR을 환영합니다!
