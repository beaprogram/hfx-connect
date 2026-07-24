export interface AddressFields {
  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  province?: string | null;
  postalCode?: string | null;
}

/** Each present line rendered separately; nothing renders for a field that's absent. */
export function addressLines(address: AddressFields): string[] {
  const lines: string[] = [];
  if (address.addressLine1) lines.push(address.addressLine1);
  if (address.addressLine2) lines.push(address.addressLine2);

  const cityProvince = [address.city, address.province].filter((part): part is string => Boolean(part)).join(", ");
  const cityProvincePostal = [cityProvince, address.postalCode].filter((part): part is string => Boolean(part)).join(" ");
  if (cityProvincePostal) lines.push(cityProvincePostal);

  return lines;
}
