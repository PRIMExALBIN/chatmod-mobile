# Phone-Hosted YouTube Live Moderation Bot Business Plan

Date: 2026-06-07

## 1. Executive Summary

Build an Android-first YouTube Live moderation bot that runs from the creator's phone and posts/moderates through the creator's own dedicated YouTube bot channel. The product gives small and mid-sized creators a custom bot identity without relying on a cloud bot name such as StreamElements, Nightbot, or another branded service.

The key promise:

> Your YouTube live chat bot, your name, hosted from your phone.

The first version should focus on practical live moderation:

- Custom YouTube bot account support
- Commands and timed messages
- Banned words and spam filters
- Delete, timeout, and ban actions
- Live mobile dashboard
- Reliable Android foreground hosting

The opportunity is not that chatbots do not exist. They do. The opportunity is that most YouTube bot tools are cloud-hosted, desktop-hosted, branded, or more complicated than smaller creators want. A phone-hosted, local-first bot with a custom channel identity can be positioned as a simple, creator-owned alternative.

## 2. Product Concept

The product is a mobile app that connects to YouTube Live Chat using Google OAuth. A streamer creates or chooses a YouTube channel to act as the bot account, adds that account as a moderator on their livestream, and then uses the Android app to run commands, automod, and chat actions locally from their phone.

### Product Name

Status: **name not final**

Naming direction:

- Short, memorable, and a little sharp
- No generic "AI", "bot", "stream", "chat", "pilot", or "mate" naming
- Should feel like creator infrastructure, not a toy
- Should work as both the app name and the bot identity
- Should sound good in a sentence: "I run my YouTube chat through ___"

Selected name:

## **ChatMod Mobile**

Product subtitle:

> Custom YouTube live chat bot from your phone.

Why it works:

- It says what the app does immediately: chat moderation.
- It makes the mobile/phone-hosted differentiator obvious.
- It is short, direct, and easy for creators to remember.
- It avoids vague metaphor names that need explanation.
- It avoids AI buzzwords and fake-slick naming.
- It works as both an app name and a bot identity.

Best app-store style listing:

> **ChatMod Mobile**  
> Custom YouTube live chat bot from your phone

Availability note:

The name is strong but still needs legal and marketplace clearance. There are existing products and projects using ChatMod or close variants, including a Twitch-focused AI moderation product. The **Mobile** suffix helps distinguish this product around the phone-hosted use case.

Fallback variants:

- **ChatMod Live**
- **YT ChatMod Mobile**
- **ChatMod for YouTube**

Names rejected:

- **ROPELINE** - too metaphorical; does not instantly say what the app is
- **HOLDFAST** - sturdy but not descriptive enough
- **LiveMod Host** - descriptive but clunky
- **TubeMod** - clear, but already used by a YouTube customization extension
- **TubeBot** - clear, but already used by other YouTube bot/tools
- **BotHost** - clear, but already used by bot-hosting services
- **ModHost** - already used by a hosting service
- **ModDeck** - already used by a streamer/moderator panel
- **HECKLE** - strong, but already close to YouTube creator tooling
- **GAVEL** - too legally loaded and already used by moderation/legal products
- **AIRLOCK** - already close to live-stream safety tooling
- **NOISEGATE** - great metaphor, but too tied to existing audio/app usage

## 3. Problem

YouTube streamers often need moderation tools, but existing options have tradeoffs:

- Cloud bots use a shared or branded bot identity.
- Some services do not support changing the bot name on YouTube.
- Desktop tools can be powerful but require a PC to stay running.
- Smaller creators streaming from console, phone, or lightweight setups may not want a desktop control stack.
- YouTube's native moderation tools are useful but limited for custom commands, timers, nuanced automod, and creator-specific workflows.

## 4. Target Customers

### Primary Segment

Small to mid-sized YouTube Live creators who want more control over chat without running a PC bot.

Examples:

- Console streamers
- Mobile streamers
- IRL streamers
- Small gaming channels
- Education/live Q&A channels
- Religious/community livestreams
- Local sports/event streamers
- Music livestreamers

### Secondary Segment

Moderators who help creators manage live chat and want a mobile control panel.

### Early Adopter Profile

- Streams on YouTube at least once per week
- Has 10 to 500 concurrent viewers
- Finds StreamElements/Nightbot too branded or unreliable
- Wants a bot with a custom name
- Uses Android or has access to an Android device during streams

## 5. Value Proposition

### Main Value

Creators can run a YouTube moderation bot with their own bot channel name directly from their phone.

### Supporting Value

- No PC required
- No shared cloud bot identity
- Local-first configuration
- Simple setup
- Fast emergency controls
- Creator-owned bot account
- Lower ongoing infrastructure costs than cloud-first bot services

## 6. Competitive Landscape

### Existing Products

| Product | Strength | Gap For This Idea |
| --- | --- | --- |
| StreamElements | Known brand, YouTube chatbot support | YouTube chatbot uses StreamElements identity and documentation says the name cannot be changed for YouTube |
| Nightbot | Reliable commands and moderation | Cloud-hosted shared bot identity |
| Fossabot | Strong cloud bot features | Not phone-hosted |
| DayBot | YouTube-specific AI bot and moderation | Cloud dashboard, not a local phone-hosted custom bot account product |
| Looomy | AI FAQ/copilot for YouTube chat | Focused on AI replies, limited moderation permissions by its own claims |
| Streamer.bot | Powerful local automation | Desktop-first, more complex |
| Mix It Up | Supports separate bot accounts including YouTube | Desktop-first |
| Stream buddy | Android stream companion with YouTube chat | Basic YouTube integration, not positioned as a full phone-hosted bot |

### Market Gap

There are many YouTube chatbot tools, but there appears to be room for:

> A mobile-first YouTube Live bot that runs locally on Android and uses the creator's own dedicated YouTube bot channel.

## 7. Differentiation

### Core Differentiator

Phone-hosted custom bot account for YouTube Live.

### Practical Differentiators

- Android foreground service keeps the bot running during streams.
- Local rules engine means the bot can work without a paid server plan.
- Emergency mode can be activated from the phone in one tap.
- Simple creator setup compared with advanced desktop tools.
- Privacy-friendly positioning: settings and logs can stay local by default.

### What Not To Compete On First

Avoid trying to beat large bot platforms on every feature immediately:

- Complex loyalty economies
- Massive overlay libraries
- Multi-platform automation
- Deep OBS scene automation
- Full analytics suites

Win first on simplicity, custom identity, and phone-hosted reliability.

## 8. MVP Feature Set

### Must Have

- Google OAuth login
- Select active YouTube livestream
- Connect to live chat
- Send bot messages
- Custom commands
- Timed messages
- Banned word filter
- Link filter
- Repeated-message spam detection
- Caps spam detection
- Delete messages
- Timeout/hide users where YouTube API permissions allow
- Ban/hide user action
- Add bot account setup checklist
- Android foreground service
- Reconnect handling
- Live status notification
- Basic moderation log
- Import/export settings as JSON

### Should Have

- Command cooldowns
- Mod-only commands
- Ignore owner/mods/members
- User whitelist
- Strike system
- Emergency lockdown mode
- Test mode for dry runs
- Battery warning
- Network quality warning

### Later

- AI moderation suggestions
- Knowledge-base FAQ bot
- Viewer loyalty points
- Giveaways
- OBS overlay
- Discord alerts
- Cloud backup
- Web dashboard
- iOS companion app

## 9. User Flow

1. User installs Android app.
2. User signs in with Google.
3. App explains that the bot name comes from the signed-in YouTube channel.
4. User creates or selects a dedicated bot channel.
5. User adds the bot channel as a moderator in YouTube Studio.
6. User starts a public livestream.
7. App detects or asks user to select the active livestream.
8. User enables bot.
9. Bot reads chat and applies rules.
10. User monitors actions from the mobile dashboard.
11. After stream, user reviews moderation log and adjusts rules.

## 10. Business Model

### Recommended Model

Freemium Android app with optional Pro subscription.

### Free Tier

- 1 YouTube channel
- 10 custom commands
- 3 timed messages
- Basic banned word filter
- Basic spam filter
- Local logs for current stream
- Manual dashboard controls

### Pro Tier

Suggested price: **$4.99/month** or **$39.99/year**

Includes:

- Unlimited commands
- Unlimited timers
- Advanced automod
- Strike history
- Emergency mode
- Export/import presets
- Longer local history
- Cloud backup optional
- Discord webhook alerts
- AI suggestions with usage limit

### Creator Tier

Suggested price: **$9.99/month** or **$79.99/year**

Includes:

- Multiple channel profiles
- Multiple bot accounts
- Advanced analytics
- Team moderator access
- AI FAQ bot
- Overlay widgets
- Priority support

### One-Time Purchase Option

Offer lifetime local-only unlock for early users:

- $19.99 to $29.99 one-time
- No cloud features
- Good for early adoption and goodwill

## 11. Revenue Opportunities

Primary:

- Mobile app subscription
- One-time local Pro unlock

Secondary:

- Paid theme packs for overlays
- Paid rule preset packs
- White-label bot setup service for creators
- Agency/team accounts
- Affiliate partnerships with creator tools

Avoid relying on ads. Creators using a moderation tool during a livestream will not want ads inside the control app.

## 12. Cost Structure

### Low-Cost MVP

Because the bot runs locally, MVP costs can stay low:

- Google Play developer account
- Domain and landing page
- Minimal backend for license/subscription verification
- Optional crash reporting and analytics
- Support email/helpdesk

### Costs If Cloud Features Are Added

- User auth backend
- Database for cloud backups
- AI API usage
- Log storage
- Web dashboard hosting
- Email service
- Monitoring

### Cost Control Strategy

- Keep core bot logic local.
- Make cloud sync optional.
- Put AI features behind Pro usage limits.
- Store only necessary account metadata.
- Avoid hosting live chat polling on company servers unless required.

## 13. Technical Plan

### Platform

Start with Android.

Reason:

- Android foreground services are better suited for long-running background work.
- Many mobile and IRL streamers use Android devices.
- iOS background execution is more restrictive.

### Core Architecture

- Android app UI
- Foreground moderation service
- YouTube API client
- OAuth token manager
- Local SQLite database
- Local rules engine
- Local logs
- Optional cloud sync layer later

### Important Technical Requirements

- Respect YouTube API quota and polling intervals.
- Handle token refresh safely.
- Use minimal OAuth scopes where possible.
- Clearly explain permissions.
- Add retry/backoff on network errors.
- Detect stream ended state.
- Avoid sending messages too quickly.
- Keep all moderation actions logged.

## 14. Legal, Policy, And Platform Risks

### YouTube API Restrictions

The app must comply with YouTube API policies and Google OAuth requirements.

Risk:

- OAuth verification may be needed before public launch, especially with sensitive scopes or wider distribution.

Mitigation:

- Use the minimum required scopes.
- Prepare a clear privacy policy.
- Make data deletion and disconnect flows obvious.
- Keep an audit log.
- Avoid scraping or unofficial APIs in the production product.

### Bot Account Misuse

Risk:

- Users may configure the bot to spam chat.

Mitigation:

- Built-in rate limits.
- Default cooldowns.
- Warnings for risky settings.
- Anti-spam guardrails.

### Battery And Reliability

Risk:

- Android may kill background work on some devices.

Mitigation:

- Foreground service with persistent notification.
- Battery optimization setup guide.
- Reconnect watchdog.
- Clear status indicator.

## 15. Go-To-Market Strategy

### Positioning

> A custom YouTube Live bot that runs from your phone.

### Best Initial Channels

- YouTube creator communities
- Reddit communities for YouTube creators and streaming
- Discord servers for small streamers
- Android livestreaming communities
- IRL streaming communities
- TikTok/Shorts demos showing setup and live moderation

### Launch Content Ideas

- "How to get a custom bot name on YouTube Live without a PC"
- "I built a YouTube chat moderator that runs from my phone"
- "Phone-hosted bot vs StreamElements/Nightbot"
- "Console streamer setup: custom YouTube bot with no PC"

### Early Beta Strategy

Recruit 20 to 50 creators who stream regularly.

Offer:

- Free Pro for 6 months
- Direct Discord support
- Feature request channel
- Founder badge or early supporter pricing

## 16. Validation Plan

### Questions To Validate

- Do YouTube creators care enough about custom bot identity to switch?
- Do creators trust a phone-hosted bot during a live stream?
- Is Android-only acceptable for the first launch?
- Which features matter more: moderation, commands, or AI replies?
- Will users pay monthly, or do they prefer one-time purchase?

### Experiments

1. Landing page waitlist
   - Goal: 100 signups from creator communities

2. Demo video
   - Show bot running from Android and deleting spam in live chat
   - Goal: 5 percent viewer-to-waitlist conversion

3. Manual beta onboarding
   - Help 20 creators set up bot accounts
   - Goal: 10 creators use it in a real stream

4. Pricing test
   - Offer $4.99/month, $39.99/year, and $24.99 lifetime local unlock
   - Goal: identify preferred pricing model

## 17. Key Metrics

### Product Metrics

- Streams moderated per week
- Average session length
- Bot uptime during stream
- Reconnect success rate
- Messages processed
- Moderation actions taken
- False positive reports
- Commands triggered

### Business Metrics

- Waitlist conversion
- Free-to-Pro conversion
- Monthly active creators
- Churn
- Support tickets per active user
- Revenue per active creator

## 18. Roadmap

### Phase 1: Prototype

Timeline: 2 to 4 weeks

- OAuth test app
- Connect to one active YouTube live chat
- Read messages
- Send a message
- Delete a message
- Simple banned word rule
- Android foreground service proof of concept

### Phase 2: MVP Beta

Timeline: 6 to 10 weeks

- Commands
- Timers
- Spam filters
- Dashboard
- Logs
- Reconnect handling
- Bot account setup checklist
- Export/import settings
- Closed beta with real creators

### Phase 3: Public Launch

Timeline: 3 to 4 months

- Google OAuth verification
- Google Play listing
- Landing page
- Privacy policy
- Subscription/payment flow
- Onboarding tutorials
- Support Discord

### Phase 4: Pro Features

Timeline: 4 to 8 months

- Advanced automod
- Strike history
- Emergency lockdown
- Discord webhooks
- Cloud backup
- AI moderation suggestions
- AI FAQ replies

## 19. MVP Success Criteria

The MVP is successful if:

- 20 creators run the bot in at least one real livestream.
- 10 creators run it in two or more livestreams.
- Bot uptime is above 95 percent during beta streams.
- Fewer than 5 percent of moderation actions are reported as bad false positives.
- At least 5 beta users say they would pay for Pro.

## 20. Biggest Risks

### Risk 1: YouTube API/OAuth Friction

Users may struggle with Google permissions and bot channel setup.

Mitigation:

- Make onboarding extremely clear.
- Add setup checklist.
- Add "test bot" button.
- Create tutorial videos.

### Risk 2: Phone Reliability

Phone battery, background restrictions, and network issues can break trust.

Mitigation:

- Android foreground service.
- Persistent status notification.
- Battery optimization guide.
- Reconnect watchdog.

### Risk 3: Existing Desktop Tools Are Enough

Some users may prefer Streamer.bot or Mix It Up.

Mitigation:

- Target creators without PC-based setups.
- Emphasize phone-hosted simplicity.
- Keep the product lighter and easier.

### Risk 4: Custom Name Is Not A Big Enough Need

The market may not pay only for custom identity.

Mitigation:

- Bundle identity with practical moderation, commands, and emergency controls.
- Sell reliability and mobile convenience, not just name customization.

## 21. Recommended First Build

Build the smallest useful version:

- Android only
- One YouTube bot account
- One active stream
- Commands
- Timers
- Banned words
- Link blocking
- Spam repeat detection
- Delete/timeout/ban controls
- Foreground service
- Live dashboard
- Local logs

Do not start with AI, overlays, loyalty points, or multi-platform support. Those can come later after validating that creators want the phone-hosted custom bot workflow.

## 22. References

- YouTube Live Streaming API: https://developers.google.com/youtube/v3/live/docs
- YouTube Live Chat Messages API: https://developers.google.com/youtube/v3/live/docs/liveChatMessages
- StreamElements YouTube chatbot documentation: https://support.streamelements.com/hc/en-us/articles/18486326016402-StreamElements-Chatbot-on-YouTube
- Mix It Up bot account documentation: https://wiki.mixitupapp.com/en/accounts
- Streamer.bot YouTube support: https://streamer.bot/
- DayBot: https://daybot.tv/
- Looomy: https://www.looomy.com/
- Stream buddy Android app: https://play.google.com/store/apps/details?id=com.streamomation.streamerchat&hl=en
