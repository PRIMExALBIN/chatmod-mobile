const dashboard = document.querySelector("[data-admin-dashboard]");

if (dashboard) {
  const form = dashboard.querySelector("[data-admin-form]");
  const originInput = dashboard.querySelector("[data-admin-origin]");
  const adminKeyInput = dashboard.querySelector("[data-admin-key]");
  const deviceInput = dashboard.querySelector("[data-device-id]");
  const installInput = dashboard.querySelector("[data-install-id]");
  const statusNode = dashboard.querySelector("[data-admin-status]");
  const resultSections = dashboard.querySelectorAll("[data-dashboard-results]");
  const entitlementForm = dashboard.querySelector("[data-entitlement-form]");
  const ticketForm = dashboard.querySelector("[data-ticket-form]");

  const rememberedOrigin = sessionStorage.getItem("chatmodAdminOrigin");
  if (originInput) {
    originInput.value = window.CHATMOD_ADMIN_API_ORIGIN || rememberedOrigin || "";
  }

  form?.addEventListener("submit", async (event) => {
    event.preventDefault();
    await loadSupportSnapshot("device");
  });

  dashboard.querySelector("[data-load-beta]")?.addEventListener("click", async () => {
    deviceInput.value = "launch-site-beta-interest";
    installInput.value = "";
    await loadSupportSnapshot("beta");
  });

  dashboard.querySelector("[data-check-health]")?.addEventListener("click", async () => {
    await checkHealth();
  });

  entitlementForm?.addEventListener("submit", async (event) => {
    event.preventDefault();
    await saveEntitlement(new FormData(entitlementForm));
  });

  ticketForm?.addEventListener("submit", async (event) => {
    event.preventDefault();
    await saveTicketMetadata(new FormData(ticketForm));
  });

  async function loadSupportSnapshot(mode) {
    const deviceId = deviceInput.value.trim();
    const installId = installInput.value.trim();
    if (!deviceId) {
      setStatus("Enter a device ID, or use the beta-interest shortcut.", "error");
      deviceInput.focus();
      return;
    }

    setStatus("Loading support snapshot...", "loading");
    try {
      const path = mode === "beta"
        ? `/admin/support/devices/${encodeURIComponent(deviceId)}`
        : `/admin/support/users?deviceId=${encodeURIComponent(deviceId)}${installId ? `&installId=${encodeURIComponent(installId)}` : ""}`;
      const snapshot = await adminFetch(path);
      renderSnapshot(snapshot);
      setStatus(`Loaded ${snapshot.deviceId}.`, "success");
    } catch (error) {
      setStatus(errorMessage(error), "error");
    }
  }

  async function checkHealth() {
    setStatus("Checking backend health...", "loading");
    try {
      const response = await fetch(apiUrl("/health/ready"), {
        method: "GET",
        credentials: "omit"
      });
      if (!response.ok) {
        throw new Error(await responseMessage(response));
      }
      setStatus("Backend readiness check passed.", "success");
    } catch (error) {
      setStatus(errorMessage(error), "error");
    }
  }

  async function saveEntitlement(formData) {
    const deviceId = deviceInput.value.trim();
    if (!deviceId) {
      setStatus("Load or enter a device ID before saving entitlement changes.", "error");
      deviceInput.focus();
      return;
    }

    setStatus("Saving entitlement adjustment...", "loading");
    try {
      await adminFetch("/admin/support/entitlements/manual-adjust", {
        method: "POST",
        body: compactPayload({
          deviceId,
          installId: installInput.value.trim(),
          plan: formData.get("plan"),
          status: formData.get("status"),
          ticketId: formData.get("ticketId"),
          note: formData.get("note")
        })
      });
      await loadSupportSnapshot("device");
      setStatus("Entitlement adjustment saved and audited.", "success");
    } catch (error) {
      setStatus(errorMessage(error), "error");
    }
  }

  async function saveTicketMetadata(formData) {
    const deviceId = deviceInput.value.trim();
    if (!deviceId) {
      setStatus("Load or enter a device ID before saving ticket metadata.", "error");
      deviceInput.focus();
      return;
    }

    setStatus("Saving ticket metadata...", "loading");
    try {
      await adminFetch("/admin/support/tickets/metadata", {
        method: "POST",
        body: compactPayload({
          deviceId,
          installId: installInput.value.trim(),
          ticketId: formData.get("ticketId"),
          status: formData.get("status"),
          priority: formData.get("priority"),
          tags: tagsFrom(formData.get("tags")),
          note: formData.get("note")
        })
      });
      ticketForm.reset();
      await loadSupportSnapshot("device");
      setStatus("Ticket metadata saved.", "success");
    } catch (error) {
      setStatus(errorMessage(error), "error");
    }
  }

  async function adminFetch(path, options = {}) {
    const adminKey = adminKeyInput.value.trim();
    if (!adminKey) {
      adminKeyInput.focus();
      throw new Error("Enter the admin API key before using the dashboard.");
    }

    const response = await fetch(apiUrl(path), {
      method: options.method || "GET",
      credentials: "omit",
      headers: {
        "content-type": "application/json",
        "x-admin-api-key": adminKey
      },
      body: options.body ? JSON.stringify(options.body) : undefined
    });
    if (!response.ok) {
      throw new Error(await responseMessage(response));
    }
    return response.json();
  }

  function apiUrl(path) {
    const origin = originInput.value.trim().replace(/\/+$/, "");
    if (!origin) {
      originInput.focus();
      throw new Error("Enter the backend origin first.");
    }
    sessionStorage.setItem("chatmodAdminOrigin", origin);
    return `${origin}${path}`;
  }

  function renderSnapshot(snapshot) {
    resultSections.forEach((section) => {
      section.hidden = false;
    });

    setText("[data-user-label]", snapshot.user?.displayName || snapshot.user?.email || "No backend user");
    setText("[data-device-label]", `${snapshot.deviceId}${snapshot.installId ? ` / ${snapshot.installId}` : ""}`);
    setText("[data-plan-label]", titleCase(snapshot.entitlement?.plan || "starter"));
    setText("[data-subscription-label]", subscriptionLabel(snapshot));
    setText("[data-profile-count]", String(snapshot.profileCount || 0));
    setText("[data-linked-label]", linkedAccountLabel(snapshot.linkedAccounts || []));

    renderList("[data-support-events]", snapshot.supportEvents || [], supportEventNode);
    renderList("[data-api-errors]", snapshot.apiErrors || [], apiErrorNode);
  }

  function renderList(selector, items, renderer) {
    const node = dashboard.querySelector(selector);
    node.replaceChildren();
    if (!items.length) {
      const empty = document.createElement("p");
      empty.className = "empty-copy";
      empty.textContent = "No recent records for this device.";
      node.append(empty);
      return;
    }
    items.slice(0, 20).forEach((item) => node.append(renderer(item)));
  }

  function supportEventNode(event) {
    return eventNode({
      title: event.message || "Support event",
      meta: [event.severity, formatDate(event.createdAt)].filter(Boolean).join(" / "),
      body: detailsPreview(event.details)
    });
  }

  function apiErrorNode(error) {
    return eventNode({
      title: error.message || error.error || "API error",
      meta: [error.method, error.path, error.statusCode, formatDate(error.createdAt)].filter(Boolean).join(" / "),
      body: detailsPreview(error.details)
    });
  }

  function eventNode(input) {
    const article = document.createElement("article");
    article.className = "event-row";
    const title = document.createElement("strong");
    title.textContent = input.title;
    const meta = document.createElement("small");
    meta.textContent = input.meta || "No metadata";
    const body = document.createElement("p");
    body.textContent = input.body || "No details";
    article.append(title, meta, body);
    return article;
  }

  function setStatus(message, state) {
    statusNode.textContent = message;
    statusNode.dataset.state = state;
  }

  function setText(selector, value) {
    const node = dashboard.querySelector(selector);
    if (node) {
      node.textContent = value;
    }
  }
}

function compactPayload(input) {
  return Object.fromEntries(
    Object.entries(input)
      .map(([key, value]) => [key, normalizeValue(value)])
      .filter(([, value]) => {
        if (Array.isArray(value)) {
          return value.length > 0;
        }
        return typeof value === "string" ? value.length > 0 : value !== null && value !== undefined;
      })
  );
}

function normalizeValue(value) {
  if (typeof value === "string") {
    return value.trim();
  }
  return value;
}

function tagsFrom(value) {
  if (typeof value !== "string") {
    return [];
  }
  return value
    .split(",")
    .map((tag) => tag.trim())
    .filter(Boolean)
    .slice(0, 12);
}

async function responseMessage(response) {
  try {
    const body = await response.json();
    if (typeof body.message === "string" && body.message) {
      return body.message;
    }
    if (typeof body.error === "string" && body.error) {
      return body.error;
    }
  } catch {
    // Static host and proxy failures can return non-JSON responses.
  }
  return `Request failed with HTTP ${response.status}.`;
}

function errorMessage(error) {
  return error instanceof Error && error.message
    ? error.message
    : "The dashboard request failed. Check the backend origin, admin key, and CORS origin.";
}

function subscriptionLabel(snapshot) {
  if (!snapshot.subscription) {
    return "No subscription record";
  }
  return `${snapshot.subscription.status || "unknown"} / ${snapshot.subscription.productId || "no product"}`;
}

function linkedAccountLabel(accounts) {
  const linked = accounts.filter((account) => account.provider === "google");
  if (!linked.length) {
    return "No linked channel";
  }
  return linked
    .map((account) => account.channelTitle || account.channelId || account.providerAccountId || "Google account")
    .slice(0, 2)
    .join(", ");
}

function detailsPreview(details) {
  if (!details || typeof details !== "object") {
    return "";
  }
  const text = JSON.stringify(details);
  return text.length > 220 ? `${text.slice(0, 220)}...` : text;
}

function formatDate(value) {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  return date.toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  });
}

function titleCase(value) {
  return String(value)
    .replace(/[-_]/g, " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}
