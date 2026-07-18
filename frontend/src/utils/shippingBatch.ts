export interface BatchShipItem {
  requestNo: string;
  trackingNo: string;
  shipperCode: string;
}

export interface BatchShipParseResult {
  items: BatchShipItem[];
  errors: string[];
}

export type BatchShipRow = Array<string | number | undefined | null>;

const HEADER_VALUES = new Set(['寄样单ID', '寄样单号', 'requestNo', 'id']);

export function parseBatchShipRows(rows: BatchShipRow[]): BatchShipParseResult {
  const items: BatchShipItem[] = [];
  const errors: string[] = [];

  for (let i = 0; i < rows.length; i += 1) {
    const row = rows[i] || [];
    if (i === 0) {
      const first = String(row[0] ?? '').trim();
      if (HEADER_VALUES.has(first)) continue;
    }

    const requestNo = String(row[0] ?? '').trim();
    const shipperCode = String(row[1] ?? '').trim();
    const trackingNo = String(row[2] ?? '').trim();

    if (!requestNo) continue;
    if (!trackingNo) {
      errors.push(`第 ${i + 1} 行：物流单号为空，已跳过`);
      continue;
    }
    if (!shipperCode) {
      errors.push(`第 ${i + 1} 行：物流公司编码为空，已跳过`);
      continue;
    }

    items.push({
      requestNo,
      trackingNo,
      shipperCode
    });
  }

  return { items, errors };
}
