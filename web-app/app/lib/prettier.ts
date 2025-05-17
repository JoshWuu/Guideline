export function Formatter(text: string) {
  return text
    .trim()
    .split(/\r?\n/)
    .filter((l) => l && !l.startsWith('*'))  // strip comments
    .map((line) => {
      const [name, from, to, ...rest] = line.trim().split(/\s+/);
      return {
        name,
        from,
        to,
        value: rest.join(' ')
      };
    });
}