export function cents(value) {
  const [integer, fraction = ''] = String(value).split('.');
  const sign = integer.startsWith('-') ? -1n : 1n;
  return BigInt(integer) * 100n + sign * BigInt(fraction.padEnd(2, '0'));
}
export function decimal(value) {
  const sign = value < 0n ? '-' : '';
  const digits = (value < 0n ? -value : value).toString().padStart(3, '0');
  return `${sign}${digits.slice(0,-2)}.${digits.slice(-2)}`;
}
export function monthDate(first, offset) {
  const [year, month, day] = first.split('-').map(Number);
  const target = new Date(Date.UTC(year, month - 1 + offset, 1));
  const last = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth()+1, 0)).getUTCDate();
  target.setUTCDate(Math.min(day, last));
  return target.toISOString().slice(0,10);
}
