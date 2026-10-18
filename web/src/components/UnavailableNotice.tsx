export function UnavailableNotice({ message }: { message: string }) {
  return (
    <p role="alert" className="notice notice--danger">
      {message}
    </p>
  );
}
