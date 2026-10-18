import type { components } from "./schema";

type Schemas = components["schemas"];

export type Drop = Schemas["Drop"];
export type DropPage = Schemas["DropPage"];
export type DropStatus = Drop["status"];
export type CreateDropRequest = Schemas["CreateDropRequest"];
export type Order = Schemas["Order"];
export type OrderPage = Schemas["OrderPage"];
export type OrderStatus = Order["status"];
export type User = Schemas["User"];
export type TokenPair = Schemas["TokenPair"];
export type ErrorResponse = Schemas["ErrorResponse"];
export type FieldError = NonNullable<ErrorResponse["errors"]>[number];
