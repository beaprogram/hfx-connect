import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RegisterForm } from "./register-form";
import { register } from "@/lib/api/auth";
import { ApiRequestError } from "@/lib/api/errors";

jest.mock("@/lib/api/auth");
const mockRegister = register as jest.MockedFunction<typeof register>;

describe("RegisterForm", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("has accessible fields with new-password autocomplete and a password hint", () => {
    render(<RegisterForm />);

    const email = screen.getByLabelText("Email");
    const password = screen.getByLabelText("Password");
    expect(email).toHaveAttribute("autoComplete", "email");
    expect(password).toHaveAttribute("autoComplete", "new-password");
    expect(password).toHaveAccessibleDescription(/at least 8 characters/i);
  });

  it("shows a success message and a link to login on success, without logging the caller in", async () => {
    mockRegister.mockResolvedValueOnce({
      id: "1",
      email: "student@example.org",
      role: "USER",
      status: "ACTIVE",
      emailVerified: false,
      createdAt: "2026-07-27T00:00:00Z",
    });
    const user = userEvent.setup();

    render(<RegisterForm />);
    await user.type(screen.getByLabelText("Email"), "student@example.org");
    await user.type(screen.getByLabelText("Password"), "correcthorsebattery");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByText("Account created")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Go to login" })).toHaveAttribute("href", "/login");
  });

  it("shows a duplicate-account message on 409 without exposing internal detail", async () => {
    mockRegister.mockRejectedValueOnce(new ApiRequestError("Conflict", 409, "USER_CONFLICT"));
    const user = userEvent.setup();

    render(<RegisterForm />);
    await user.type(screen.getByLabelText("Email"), "student@example.org");
    await user.type(screen.getByLabelText("Password"), "correcthorsebattery");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("An account with this email already exists.");
  });

  it("displays server-side field validation errors next to the relevant field", async () => {
    mockRegister.mockRejectedValueOnce(
      new ApiRequestError("Validation failed", 400, "VALIDATION_ERROR", { password: "Password is too weak." }),
    );
    const user = userEvent.setup();

    render(<RegisterForm />);
    await user.type(screen.getByLabelText("Email"), "student@example.org");
    await user.type(screen.getByLabelText("Password"), "password1");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByText("Password is too weak.")).toBeInTheDocument();
    expect(screen.getByLabelText("Password")).toHaveAttribute("aria-invalid", "true");
  });
});
