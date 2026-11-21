# T-cash-movements — Income into accounts, spending from accounts
- Status: in review
- Context: user request 2026-11-21 (received income did not change net worth); income may be split across several
  accounts by fixed amount or percentage. ADR cash-1. Server: `intoAccounts` on income_source and income_receipt, `cashAccountId` and `counterAmount` on ledger_transaction (sealed-only fields). Clients in Ri-dha/MizanApp.
- Notes: `AccountShare`, `IncomeSource.intoAccounts`, `IncomeReceipt.intoAccounts`, `LedgerTransaction.cashAccountId`;
  `db/cashMovements.ts` (`resolveShares`, `unassigned`, `movementsOf`, `withMovements`, `liveAccountsNow`);
  `inBase` rounds once. Web: `AccountSharesEditor` in the receipt and income source forms, "Paid from"/"Into account" in
  quick add (last account remembered per device) and in "mark bill paid", Cash page shows what moved each account.
  Phone: balances and the account editor use today's balance; its forms do not offer the account fields yet.
