# Deployment Guide

## GitHub Actions 자동 배포 설정

이 프로젝트는 `master` 브랜치에 푸시하면 자동으로 GCP Cloud Run에 배포됩니다.

### 필수 GitHub Secrets

다음 Secrets를 GitHub Repository Settings에 추가해야 합니다:

#### 1. GCP 관련
- **`GCP_SA_KEY`** (필수)
  - GCP Service Account의 JSON 키
  - Cloud Run 배포 권한 필요

#### 2. 데이터베이스 관련
- **`DATABASE_URL`** (필수)
  - PostgreSQL 연결 URL
  - 예시: `jdbc:postgresql://host:port/database?sslmode=require`
  
- **`DB_USERNAME`** (필수)
  - 데이터베이스 사용자명
  
- **`DB_PASSWORD`** (필수)
  - 데이터베이스 비밀번호

#### 3. 이메일 관련
- **`SENDGRID_API_KEY`** (필수)
  - SendGrid API 키
  - 이메일 발송에 사용

### 선택적 GitHub Variables

다음 Variables는 선택사항이며, 설정하지 않으면 기본값이 사용됩니다:

#### GCP 설정
- **`GCP_PROJECT_ID`** (기본값: `peppy-coda-471714-p7`)
- **`GCP_REGION`** (기본값: `asia-northeast3`)
- **`CLOUD_RUN_SERVICE`** (기본값: `newsletter-service`)

#### 애플리케이션 설정
- **`MAIL_FROM`** (기본값: `no-reply@example.com`)
  - 발신자 이메일 주소
  
- **`SITE_URL`** (기본값: `https://ramsbaby.netlify.app`)
  - 메인 사이트 URL
  
- **`RSS_URL`** (기본값: `https://ramsbaby.netlify.app/rss.xml`)
  - RSS 피드 URL

## 배포 방법

### 자동 배포
```bash
git add .
git commit -m "Your commit message"
git push origin master
```

`master` 브랜치에 푸시하면 자동으로 배포가 시작됩니다.

### 수동 배포
GitHub Actions 탭에서 "Deploy newsletter-service to Cloud Run" 워크플로우를 선택하고 "Run workflow" 버튼을 클릭하세요.

## 배포 확인

1. GitHub Actions 탭에서 워크플로우 실행 상태 확인
2. 워크플로우 완료 후 Summary에서 배포된 URL 확인
3. Cloud Run 콘솔에서 서비스 상태 확인

## 로컬 테스트

배포 전 로컬에서 Docker 이미지를 테스트할 수 있습니다:

```bash
# 이미지 빌드
docker build -t newsletter-service:test .

# 컨테이너 실행
docker run -p 8080:8080 \
  -e DATABASE_URL="jdbc:postgresql://..." \
  -e DB_USERNAME="..." \
  -e DB_PASSWORD="..." \
  -e SPRING_MAIL_HOST="smtp.sendgrid.net" \
  -e SPRING_MAIL_USERNAME="apikey" \
  -e SPRING_MAIL_PASSWORD="..." \
  newsletter-service:test
```

## 문제 해결

### 배포 실패 시
1. GitHub Actions 로그 확인
2. GCP Service Account 권한 확인
3. Secrets/Variables 설정 확인
4. Artifact Registry 접근 권한 확인

### 런타임 오류 시
1. Cloud Run 로그 확인: `gcloud run logs read newsletter-service --region asia-northeast3`
2. 환경 변수 설정 확인
3. 데이터베이스 연결 확인

