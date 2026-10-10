import { z } from 'zod';

export const uuid = z.string().uuid('Identificador inválido');

export const dateOnly = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, 'Use o formato AAAA-MM-DD')
  .refine((value) => {
    const date = new Date(`${value}T00:00:00Z`);
    return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(value);
  }, 'Data inválida');

// Dinheiro trafega como número ou string ("1234.50") e é normalizado para string
// com 2 casas, para casar com DECIMAL e evitar erro de ponto flutuante.
export const money = z
  .union([z.number(), z.string().trim()])
  .transform((value, ctx) => {
    const text = typeof value === 'number' ? String(value) : value;
    if (!/^\d{1,12}(\.\d{1,2})?$/.test(text)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Valor monetário inválido (use até 12 dígitos e 2 casas decimais)',
      });
      return z.NEVER;
    }
    return Number(text).toFixed(2);
  })
  .refine((value) => Number(value) > 0, 'O valor deve ser maior que zero');

export const syncVersion = z.number().int().positive();

export const pageQuery = z.object({
  page: z.coerce.number().int().min(1).default(1),
  pageSize: z.coerce.number().int().min(1).max(100).default(20),
});

export const search = z.string().trim().min(1).max(100);

export const categoriaCnh = z.enum(['A', 'B', 'C', 'D', 'E', 'AB', 'AC', 'AD', 'AE']);
