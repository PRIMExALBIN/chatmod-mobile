const betaForm = document.querySelector("[data-beta-form]");
const betaButton = document.querySelector("[data-beta-button]");
const betaNote = document.querySelector("[data-beta-note]");

if (betaForm && betaButton && betaNote) {
  betaForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const formData = new FormData(betaForm);
    const payload = compactPayload({
      email: formData.get("email"),
      creatorName: formData.get("creatorName"),
      channelUrl: formData.get("channelUrl"),
      message: formData.get("message"),
      source: "launch-site"
    });

    if (!isEmail(payload.email)) {
      setNote("Enter a valid email before saving beta interest.", "error");
      betaForm.querySelector("#email")?.focus();
      return;
    }

    betaButton.disabled = true;
    setNote("Saving beta interest...", "loading");

    try {
      const response = await fetch(betaInterestEndpoint(), {
        method: "POST",
        headers: {
          "content-type": "application/json"
        },
        credentials: "omit",
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        throw new Error(await responseMessage(response));
      }

      betaForm.reset();
      setNote(
        "Saved. We will use this email only for ChatMod Mobile beta access updates.",
        "success"
      );
    } catch (error) {
      setNote(
        error instanceof Error && error.message
          ? error.message
          : "Beta interest could not be saved right now. Try again in a minute.",
        "error"
      );
    } finally {
      betaButton.disabled = false;
    }
  });
}

function betaInterestEndpoint() {
  if (typeof window.CHATMOD_BETA_API_URL === "string" && window.CHATMOD_BETA_API_URL.trim()) {
    return window.CHATMOD_BETA_API_URL.trim();
  }

  return betaForm.getAttribute("data-beta-api") || "/feedback/beta-interest";
}

function compactPayload(input) {
  return Object.fromEntries(
    Object.entries(input)
      .map(([key, value]) => [key, typeof value === "string" ? value.trim() : value])
      .filter(([, value]) => typeof value === "string" ? value.length > 0 : Boolean(value))
  );
}

function isEmail(value) {
  return typeof value === "string" && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

async function responseMessage(response) {
  try {
    const body = await response.json();
    if (typeof body.message === "string" && body.message) {
      return body.message;
    }
  } catch {
    // The backend normally returns JSON errors, but static host failures may not.
  }

  if (response.status === 429) {
    return "Too many beta requests from this connection. Try again in a minute.";
  }

  return "Beta interest could not be saved right now. Try again in a minute.";
}

function setNote(message, state) {
  betaNote.textContent = message;
  betaNote.dataset.state = state;
}
