import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LoginForm } from "./login-form";
import { useAuth } from "@/lib/auth/auth-provider";

jest.mock("@/lib/auth/auth-provider");
const mockPush = jest.fn();
let mockSearchParams = new URLSearchParams();
jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: jest.fn() }),
  useSearchParams: () => mockSearchParams,
}));

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;

function mockAuth(login: jest.Mock) {
  mockUseAuth.mockReturnValue({
    state: { status: "unauthenticated" },
    login,
    logout: jest.fn(),
    getValidAccessToken: jest.fn(),
  });
}

describe("LoginForm", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockSearchParams = new URLSearchParams();
  });

  it("has accessible, correctly-autocompleted email and password fields", () => {
    mockAuth(jest.fn());
    render(<LoginForm />);

    const email = screen.getByLabelText("Email");
    const password = screen.getByLabelText("Password");
    expect(email).toHaveAttribute("autoComplete", "email");
    expect(password).toHaveAttribute("type", "password");
    expect(password).toHaveAttribute("autoComplete", "current-password");
  });

  it("logs in and redirects to /dashboard on success", async () => {
    const login = jest.fn().mockResolvedValue(undefined);
    mockAuth(login);
    const user = userEvent.setup();

    render(<LoginForm />);
    await user.type(screen.getByLabelText("Email"), "student@example.org");
    await user.type(screen.getByLabelText("Password"), "correcthorsebattery");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    await waitFor(() => expect(login).toHaveBeenCalledWith("student@example.org", "correcthorsebattery"));
    expect(mockPush).toHaveBeenCalledWith("/dashboard");
  });

  it("shows one generic failure message regardless of the underlying error, never revealing account existence", async () => {
    const login = jest.fn().mockRejectedValue(new Error("AUTHENTICATION_FAILED"));
    mockAuth(login);
    const user = userEvent.setup();

    render(<LoginForm />);
    await user.type(screen.getByLabelText("Email"), "nobody@example.org");
    await user.type(screen.getByLabelText("Password"), "whatever123");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Incorrect email or password.");
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("disables the submit button while submitting", async () => {
    let resolveLogin: () => void = () => {};
    const login = jest.fn().mockImplementation(() => new Promise<void>((resolve) => (resolveLogin = resolve)));
    mockAuth(login);
    const user = userEvent.setup();

    render(<LoginForm />);
    await user.type(screen.getByLabelText("Email"), "student@example.org");
    await user.type(screen.getByLabelText("Password"), "correcthorsebattery");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    expect(screen.getByRole("button", { name: "Logging in…" })).toBeDisabled();
    resolveLogin();
  });

  it("redirects to a valid returnTo path after a successful login", async () => {
    mockSearchParams = new URLSearchParams({ returnTo: "/resources/halifax-central-library" });
    const login = jest.fn().mockResolvedValue(undefined);
    mockAuth(login);
    const user = userEvent.setup();

    render(<LoginForm />);
    await user.type(screen.getByLabelText("Email"), "student@example.org");
    await user.type(screen.getByLabelText("Password"), "correcthorsebattery");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith("/resources/halifax-central-library"));
  });

  it.each([
    ["an absolute URL", "https://evil.example.org"],
    ["a protocol-relative URL", "//evil.example.org"],
    ["a backslash trick", "/\\evil.example.org"],
    ["a scheme with no leading slash", "javascript:alert(1)"],
  ])("falls back to /dashboard when returnTo is %s (rejected as unsafe)", async (_label, unsafeReturnTo) => {
    mockSearchParams = new URLSearchParams({ returnTo: unsafeReturnTo });
    const login = jest.fn().mockResolvedValue(undefined);
    mockAuth(login);
    const user = userEvent.setup();

    render(<LoginForm />);
    await user.type(screen.getByLabelText("Email"), "student@example.org");
    await user.type(screen.getByLabelText("Password"), "correcthorsebattery");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith("/dashboard"));
  });

  it("links to the registration page", () => {
    mockAuth(jest.fn());
    render(<LoginForm />);

    expect(screen.getByRole("link", { name: "Register" })).toHaveAttribute("href", "/register");
  });
});
