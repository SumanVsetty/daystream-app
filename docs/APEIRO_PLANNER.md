# Apeiro planner

Apeiro extends Ivy Wallet with a bullet-journal style planner: one timeline for tasks,
events, notes and (later) journal entries, routines and trackers, alongside expenses.

## Modules

- `:shared:planner` – planner domain logic and database, no UI.
  - `domain/` – pure Kotlin, fully unit tested:
    - `IsoWeek` – ISO 8601 (Swedish) week numbers, weeks start Monday.
    - `RepeatRule`, `RepeatSchedule` – daily / weekly on chosen days / monthly on a day,
      nth weekday or last day / yearly (12 months) / N days after completion; start and end.
    - `RepeatCodec` – stores rules as short strings and describes them in English.
    - `Planner` – what shows on a day, the missed-day rule, overdue, week tasks,
      weekly review candidates, consistency ("done 26 of last 30").
  - `data/` – Room database `apeiro_planner.db`, separate from Ivy's money database.
- `:feature:planner` – Compose screens: Day log, Week log, Weekly review, Editor, Search.

## Key rules

- A repeating task is a **series** (the master) plus **occurrence records**, stored only when
  a day is done, skipped or edited "only today". Unticked past days are derived as MISSED,
  so there is no midnight job and nothing piles up as overdue.
- "This day and all future days" ends the old series the day before and starts a new one,
  so past days keep their history.
- One-off tasks left open become "Needs a decision" (Done / Today / Schedule / Drop).
  Moving a task out of the past counts as a migration (`migration_count`).
- The weekly review steps through last week's open tasks: Done, Migrate (to this week),
  Schedule, Drop.

## Already in the schema for later versions

Routine steps with per-day progress and snapshots, checklists, collections (boards and
life topics like Car or Suchet), people, importance, attachments, trackers with readings,
and a full-text search index.

## Roadmap

1. v1 (this): Day log, Week log, weekly review, repeating tasks, editor, search.
2. Backup: include the planner database and attachments in Apeiro's backup (zip).
3. Reminders and notifications (Done / Snooze / Tomorrow, reschedule sheet).
4. Boards and checklists.
5. Routine player.
6. Journal: importance, people, collections, photos; person and collection timelines.
7. Trackers with charts; search filters.
8. Make the Day log the home screen, with Money as a tab.
