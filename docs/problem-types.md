# Problem types

Every error is an RFC 9457 `application/problem+json` body. Its `type` links to a
heading on this page, and every body carries the `requestId` that is also in the
`X-Request-Id` header and the logs.

## validation-error
`400`. A request field failed validation. `errors` lists each field and its message.

## malformed-request
`400`. The body is not valid JSON, or a value has the wrong type. No parser detail is returned.

## invalid-idempotency-key
`400`. The `Idempotency-Key` header is blank or longer than 128 characters.
A missing header is also a `400`, reported by Spring as a missing required header.

## malformed-if-match
`400`. `If-Match` is not a quoted version such as `"3"`.

## order-not-found
`404`. No order has that id.

## invalid-state-transition
`409`. The order cannot move to the requested status. The body names `currentStatus`,
`requestedStatus` and `allowedTransitions`.

## order-not-editable
`409`. Lines can only be replaced while the order is `DRAFT`.

## concurrent-modification
`409`. Another request changed the order between this request's read and its write.
Read the order again and retry.

## idempotency-key-in-use
`409`. Another request with the same key is being processed at this moment.

## version-mismatch
`412`. `If-Match` names a version that is no longer current. The body gives
`expectedVersion` and `currentVersion`. Nothing was written.

## idempotency-key-payload-mismatch
`422`. The key was already used, within 24 hours, for a request with a different payload.

## precondition-required
`428`. `PUT /orders/{id}/lines` needs `If-Match` with the current version.

## internal-error
`500`. An unexpected failure. The body contains no exception detail; use the `requestId`
to find it in the logs.
