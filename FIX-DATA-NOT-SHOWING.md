# Fix: tabs showed empty state even though the database had data
Cause: Home/Subscriptions/Alerts/AI fragments never queried Supabase (hardcoded empty state; no GET endpoints).

New: DataDtos.java, DataRepository.java, RowRenderer.java, item_row.xml
Changed: SupabaseDatabaseApi (4 GET endpoints), the 4 tab Fragments, 4 layouts (listContainer / stats), strings.xml

UI polish: gradient hero, icon chips, row icons, softer empty states, entrance animation (no logic changes).

Behaviour: reads subscriptions / alerts / ai_insights tables; if a table is empty, derives the view from financial_events
(what the SMS + Gmail sync writes). Pull down to refresh; tabs also refresh when you switch to them.
If a request fails (e.g. RLS/network), the reason is shown on the screen instead of a silent empty state.
No Gradle changes. Do Sync + Rebuild.
