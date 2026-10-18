import Link from "next/link";

export default function NotFound() {
  return (
    <main className="page">
      <h1 className="page-title__heading">찾을 수 없습니다</h1>
      <p className="muted" style={{ margin: 0 }}>
        드롭이나 주문이 없거나 주소가 잘못됐습니다.
      </p>
      <Link href="/drops" className="button" style={{ alignSelf: "flex-start" }}>
        드롭 보드로
      </Link>
    </main>
  );
}
