# Pagos con Tebex

1. En Tebex crea un paquete por cada pack de gemas (uno por línea de `tebex-packs.json`) con el precio que quieras.
   Copia el **ID numérico** de cada paquete y pégalo en el campo `"tebex"` de `tebex-packs.json`
   (`gems` = gemas que da, `price` = solo para mostrarlo en el launcher; el cobro real es el de Tebex).
2. Tebex → Developers → API keys: copia el **token público** de la webstore.
3. Tebex → Developers → Webhooks → Endpoints: añade `https://TU-DOMINIO/api/pay/webhook`, activa los eventos
   `payment.completed`, `payment.refunded` y `payment.dispute.opened`, copia el **secreto** y pulsa **Validate**
   (el servidor ya responde a la validación; tiene que estar arrancado).
4. Arranca el servidor:
   TEBEX_PUBLIC_TOKEN=... TEBEX_WEBHOOK_SECRET=... PUBLIC_URL=https://TU-DOMINIO node server.js
   (opcional: TEBEX_CHECK_IP=1 solo acepta las IP de Tebex; con proxy/túnel pon también TRUST_PROXY=1)
5. Haz UNA compra de prueba y comprueba que llegan las gemas. Si un pago no se puede acreditar solo, queda en
   `data/pagos-sin-acreditar.log` con todo el contenido del aviso.
Hace falta dominio fijo con https y servidor siempre encendido.
