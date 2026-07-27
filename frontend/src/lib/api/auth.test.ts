import { getCurrentUser, login, logout, refreshSession, register } from "./auth";

const userBody = {
  id: "11111111-1111-1111-1111-111111111111",
  email: "student@example.org",
  role: "USER",
  status: "ACTIVE",
  emailVerified: false,
  createdAt: "2026-07-27T00:00:00Z",
};

const loginBody = {
  accessToken: "a.jwt.token",
  tokenType: "Bearer",
  expiresIn: 900,
  user: userBody,
};

function mockFetchOnce(json: unknown, status = 200) {
  (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: status < 400, status, json: async () => json } as Response);
}

describe("auth API operations", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("register posts email/password with no credentials and returns the safe user body", async () => {
    mockFetchOnce(userBody, 201);

    const result = await register({ email: "student@example.org", password: "correcthorsebattery" });

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(url).toContain("/api/v1/auth/register");
    expect(init.credentials).toBeUndefined();
    expect(JSON.parse(init.body as string)).toEqual({ email: "student@example.org", password: "correcthorsebattery" });
    expect(result.role).toBe("USER");
  });

  it("login sends credentials: include so the refresh cookie can be received cross-origin", async () => {
    mockFetchOnce(loginBody);

    const result = await login({ email: "student@example.org", password: "correcthorsebattery" });

    const [, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(init.credentials).toBe("include");
    expect(result.accessToken).toBe("a.jwt.token");
    expect(result.user.email).toBe("student@example.org");
  });

  it("refreshSession sends credentials: include and no request body", async () => {
    mockFetchOnce(loginBody);

    await refreshSession();

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(url).toContain("/api/v1/auth/refresh");
    expect(init.credentials).toBe("include");
    expect(init.body).toBeUndefined();
  });

  it("logout sends credentials: include and resolves with no body on 204", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 204 } as Response);

    await expect(logout()).resolves.toBeUndefined();

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(url).toContain("/api/v1/auth/logout");
    expect(init.credentials).toBe("include");
  });

  it("getCurrentUser sends the access token as a Bearer header, not a cookie", async () => {
    mockFetchOnce(userBody);

    await getCurrentUser("a.jwt.token");

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(url).toContain("/api/v1/users/me");
    expect((init.headers as Record<string, string>).Authorization).toBe("Bearer a.jwt.token");
    expect(init.credentials).toBeUndefined();
  });

  it("login rejects with ApiRequestError on 401 without leaking whether the email exists", async () => {
    mockFetchOnce(
      { timestamp: "2026-07-27T00:00:00Z", status: 401, code: "AUTHENTICATION_FAILED", message: "Invalid email or password." },
      401,
    );

    await expect(login({ email: "nobody@example.org", password: "wrong" })).rejects.toMatchObject({
      status: 401,
      code: "AUTHENTICATION_FAILED",
    });
  });
});
