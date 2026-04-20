# Software Requirements Specification (SRS)
## Survey Engine MVP

| Field | Value |
| :---- | :---- |
| Document Title | Survey Engine MVP SRS |
| Version | 1.0 |
| Date | March 3, 2026 |
| Prepared For | Product and Engineering |
| Classification | Internal |

## 1. Purpose
Define the MVP requirements for a Survey Engine that supports survey authoring, response collection, controlled distribution, analytics, and weighted scoring for category-based evaluation.

## 2. Scope
### 2.1 In Scope (MVP)
- Survey and campaign creation using reusable question categories.
- Question bank and category management.
- Survey runtime with required/optional questions and skip logic.
- Respondent metadata collection.
- Survey-level settings and restrictions.
- Basic theming/styling.
- Results dashboard and reporting.
- Weighted scoring by question and category.

### 2.2 Out of Scope (Post-MVP)
- Advanced AI insights.
- External BI warehouse pipelines.
- Multi-tenant enterprise permission matrix beyond basic role controls.

## 3. Definitions
- Survey: A questionnaire instance delivered to respondents.
- Campaign: A delivery container for one survey and its distribution settings.
- Question Bank: Repository of reusable questions.
- Category: A logical grouping of questions from the question bank.
- Weight Profile: Category weight configuration used by evaluator/step scoring.

## 4. Functional Requirements
### 4.1 Survey Builder
- Support single-page and multi-page survey flows.
- Add and order questions.
- Mark question as mandatory or optional.
- Define skip logic (conditional navigation).
- Configure answer choice ordering for single and multiple-choice questions.
- Support question types:
  - Rank
  - Rating scale

### 4.2 Question Bank and Category Model
- System shall maintain a reusable question bank.
- System shall allow creation of categories consisting of bank questions.
- A survey/campaign shall use categories, not ad-hoc unmanaged question lists.
- Questions may have per-question weight.
- Categories may have category weight within an evaluator weight profile.
- Question bank items and category definitions shall be versioned.
- When an admin creates/publishes a survey, the survey shall reference immutable tagged versions (snapshot) of categories and questions.
- Post-publish edits to bank questions/categories shall create new versions and must not mutate already-tagged survey content.
- Historical results must remain reproducible against the original published snapshot.

### 4.3 Respondent Form Fields (Metadata)
- Support optional metadata capture before or during survey:
  - Name
  - Email
  - Address
  - Phone
  - Date/Time

### 4.4 Styling and Theming
- Select visual style from predefined templates.
- Configure custom style per survey.

### 4.5 Survey Settings
#### General Controls
- Password protection.
- CAPTCHA protection.
- One response per device/computer.
- IP restrictions.
- Email restrictions.
- Authentication mode selection per campaign:
  - Public (anonymous link)
  - External SSO required (subscriber-managed auth)
  - Token-gated access (signed launch token)

#### Behavior Controls
- Close survey by date/time.
- Close survey by response quota.
- Show/hide question numbers.
- Show/hide progress indicator.
- Show/hide back button.
- Session timeout reset for incomplete responses.

#### Start/Finish and Layout
- Configurable start message.
- Configurable finish message.
- Configurable header.
- Configurable footer.

### 4.6 Survey and Campaign Lifecycle
- Lifecycle states:
  - Draft
  - Published/Open
  - Closed
  - Results Published
  - Archived
- Allowed forward transitions:
  - Draft -> Published/Open
  - Published/Open -> Closed
  - Closed -> Results Published
  - Results Published -> Archived
- Reopen policy:
  - Closed -> Published/Open only when admin reopen policy allows it.
  - Reopen action must be explicitly audited with reason and actor.
- After transition to Published/Open, survey structure is immutable:
  - No add/remove/reorder of categories or questions.
  - No change to scoring weights or logic for that published snapshot.
  - No change to mandatory flags or answer configuration for that published snapshot.

### 4.7 Results and Analytics
#### Overview Dashboard
- Traffic chart by day/time.
- Total completed surveys.
- Completion rate.
- Incomplete response count.

#### Reports
- Question-level answer breakdown.
- Participant counts and participant metadata view.
- Filter and segment-based analysis.
- Comparative analysis across answers and metadata dimensions.

### 4.8 Distribution and Integration
- Public/private direct links.
- HTML embed.
- WordPress embed.
- JavaScript/front-end embed.
- Email distribution.

### 4.9 Authentication and Access (Best MVP Options)
#### 4.8.1 Auth Ownership Model
- Survey Engine shall not be the primary identity provider for respondent users.
- Subscriber identity is externally managed by subscriber IdP/auth systems.
- Survey Engine admin user configures auth trust and claim mapping inside Survey Engine.

#### 4.8.2 Supported Auth Modes (MVP)
- Public Anonymous Mode
  - No identity assertion required.
  - Protected by campaign controls (password, CAPTCHA, IP/email/device limits, quota, expiry).
- Signed Launch Token Mode
  - Subscriber launches survey with a short-lived signed token containing respondent identity/context.
  - Survey Engine validates signature, issuer, audience, expiry, and nonce/replay guard.
- External SSO Trust Mode
  - Survey Engine trusts subscriber IdP assertions (OIDC/JWT/SAML gateway handoff).
  - No separate Survey Engine login for respondents.

#### 4.8.3 Admin Configuration Requirements
- Configure per-tenant/per-subscriber auth profile:
  - Issuer
  - Audience
  - Verification key/JWKS endpoint/certificate
  - Allowed clock skew
  - Token TTL policy
- Configure claim mappings:
  - External user ID -> respondent external ID
  - Email claim -> respondent email
  - Optional organization/group claims for eligibility rules
- Configure fallback policy:
  - SSO required
  - Allow anonymous fallback
  - Disable launch if token validation fails

#### 4.8.4 Security and Audit Requirements
- All auth config changes must be audit logged with actor, timestamp, and before/after values.
- Signature key rotation must be supported without downtime.
- Replay protection is mandatory for signed launch mode.
- Invalid tokens/assertions must return deterministic auth error codes.
- Respondent session must be tenant-scoped and campaign-scoped.

## 5. Scoring and Weighting Requirements
### 5.1 Core Flow
1. Sum all question scores in each category.
2. Normalize category score by category maximum.
3. Apply category weight percentage.
4. Sum weighted category scores for final evaluator-step total.

### 5.2 Formula
For each category:
- Raw Category Score = sum(obtained marks for category questions)
- Max Category Score = sum(max marks for category questions)
- Category Normalized % = (Raw Category Score / Max Category Score) x 100
- Category Weighted Score = Category Normalized % x Category Weight %

Evaluator-Step total:
- Step Total = sum(Category Weighted Score across all categories)

### 5.3 Constraints
- Category weights in one evaluator weight profile must total exactly 100%.
- At survey configuration time, total category weights must not exceed 100%.
- Final publish/activate validation requires total = 100%.
- Question max score must be > 0.
- Category with zero max score is invalid for scoring.

### 5.4 Validation Errors (Minimum)
- `INVALID_WEIGHT_SUM` when category weights are not exactly 100% at activation.
- `CATEGORY_MAX_SCORE_ZERO` when category max score is zero.
- `QUESTION_MAX_SCORE_INVALID` when max score <= 0.
- `SURVEY_IMMUTABLE_AFTER_PUBLISH` when modification is attempted after publish.
- `INVALID_LIFECYCLE_TRANSITION` when state transition is not allowed.

## 6. High-Level Architecture
### 6.1 Logical Components
- Survey Builder Service
- Survey Runtime/Rendering Service
- Response Collection Service
- Rules Engine (skip logic and restrictions)
- Analytics and Reporting Service
- Integration/Delivery Service (links, embeds, email)
- Admin UI
- Respondent UI

### 6.2 Data Domains
- Survey definitions (pages, questions, logic, style, settings)
- Question bank and category mappings
- Versioned question/category entities and published survey snapshots
- Campaign definitions and distribution config
- Responses (answers, completion status, timestamps)
- Respondent metadata
- Access-control constraints (password, IP, email, device)
- Reporting aggregates and analytical snapshots

### 6.3 Scalability Requirements (MVP)
- Tenant/org partitioning must be enforced at data-access level for all survey, response, and analytics queries.
- Indexing strategy must cover high-volume query paths including:
  - tenant/org + campaign/survey filters
  - submission timestamp ranges
  - lifecycle state filters
  - respondent lookup keys (as allowed by privacy policy)
- Analytics and reporting aggregates must support asynchronous computation for large response volumes.
- Query layer must be snapshot/version-aware so all scoring and reports resolve against the exact published survey version.
- Audit subsystem must support strict append-only audit logs for:
  - publish/close/reopen/archive transitions
  - auth/profile changes
  - response reopen operations and edit windows

## 7. Non-Functional Requirements (MVP)
- Availability target: 99.5% monthly.
- P95 survey submit latency: <= 500 ms under agreed MVP load.
- All scoring operations must be deterministic and reproducible.
- Audit logs required for survey publish, campaign activation, and weight profile changes.
- Audit logs required for auth profile creation/update/deletion and claim mapping changes.
- Survey snapshot/version resolution must be deterministic for all report and scoring queries.
- Privacy: mask/store sensitive respondent fields per policy.
- Platform must support tenant/org-isolated scaling without cross-tenant data leakage.
- Analytics jobs must be resumable and idempotent for safe retry under load.

## 8. Response Locking Policy
- After successful submission, a response becomes read-only/locked.
- Locked response cannot be modified by respondent.
- Controlled reopen is admin-only and policy-driven.
- Reopen must capture:
  - reason
  - actor
  - timestamp
  - allowed edit window
- Any reopened response edit history must be retained for audit.

## 9. Acceptance Criteria
- User can build and publish survey using categories from question bank.
- Campaign can distribute survey via link/embed/email.
- Runtime enforces settings (captcha, limits, closures, restrictions).
- Reports show completion and answer breakdown.
- Weighted scoring is computed exactly per formula.
- Activation is blocked when category weights do not equal 100%.
- Admin can configure external auth trust per subscriber and successfully validate test tokens/assertions.
- Respondents can access survey without Survey Engine-managed login when external auth is configured.
- Post-publish modification attempts to survey snapshot are rejected.
- Question/category edits after publish create new versions without changing published surveys.
- Submitted responses are locked read-only unless admin-controlled reopen policy is applied.

## 10. Future Extensions
- Versioned question bank with approval workflow.
- Advanced scoring models (z-score, benchmark percentile, normalization families).
- AI-assisted insights and anomaly detection.
- Multi-tenant enterprise policy packs.
