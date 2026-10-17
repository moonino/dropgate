import type { Metadata } from "next";
import { decideLoginNotice } from "@/domain/login-errors";
import { parseRetryAfterSeconds } from "@/domain/retry";
import { isMockMode } from "@/server/config";

export const metadata: Metadata = { title: "로그인" };

type LoginPageProps = { searchParams: Promise<{ error?: string; retryAfter?: string }> };

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const { error, retryAfter } = await searchParams;
  const notice = decideLoginNotice(error, parseRetryAfterSeconds(retryAfter));
  return (
    <main className="page" style={{ maxWidth: 520, paddingTop: 80 }}>
      <div className="page-title">
        <p className="page-title__eyebrow">GATE 01 / SIGN IN</p>
        <h1 className="page-title__heading" style={{ fontSize: 40 }}>
          로그인하고 드롭에 참여하세요
        </h1>
        <p className="muted" style={{ margin: 0 }}>
          카카오 계정으로 로그인합니다. 저장하는 것은 카카오 회원 번호와 닉네임뿐입니다.
        </p>
      </div>
      {notice.kind === "error" && (
        <p role="alert" className="notice notice--danger">
          {notice.message}
        </p>
      )}
      {isMockMode() ? <MockLogin /> : <KakaoLogin />}
      <p className="muted" style={{ margin: 0, fontSize: 14 }}>
        로그인 없이도 드롭 보드는 볼 수 있습니다. 구매는 로그인이 필요합니다.
      </p>
    </main>
  );
}

function KakaoLogin() {
  return (
    <a href="/auth/login/kakao" className="button button--large button--kakao">
      카카오 로그인
    </a>
  );
}

function MockLogin() {
  return (
    <form action="/auth/mock" method="post" style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      <p className="notice" style={{ fontSize: 14 }}>
        모의 모드입니다. 카카오 대신 모의 사용자를 고릅니다.
      </p>
      <button type="submit" name="role" value="USER" className="button button--large button--primary">
        모의 사용자로 로그인
      </button>
      <button type="submit" name="role" value="ADMIN" className="button button--large">
        모의 관리자로 로그인
      </button>
    </form>
  );
}
