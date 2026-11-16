// A secret piped in from Windows PowerShell can carry a UTF-8 BOM and a newline (2026-11-12: OpenAI answered 400).
export const secret = v => String(v ?? "").replace(/^﻿/, "").trim();
