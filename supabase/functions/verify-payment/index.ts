import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

async function hmacHex(secret: string, message: string): Promise<string> {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message));
  return [...new Uint8Array(signature)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
function constantTimeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return Response.json({ error: "Method not allowed" }, { status: 405, headers: cors });

  try {
    const url = Deno.env.get("SUPABASE_URL")!;
    const anon = Deno.env.get("SUPABASE_ANON_KEY")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const keyId = Deno.env.get("RAZORPAY_KEY_ID")!;
    const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET")!;
    const auth = req.headers.get("Authorization") ?? "";
    if (!auth.startsWith("Bearer ")) return Response.json({ error: "Unauthorized" }, { status: 401, headers: cors });

    const userClient = createClient(url, anon, { global: { headers: { Authorization: auth } } });
    const { data: { user }, error: userError } = await userClient.auth.getUser();
    if (userError || !user) return Response.json({ error: "Unauthorized" }, { status: 401, headers: cors });

    const { razorpay_order_id, razorpay_payment_id, razorpay_signature, course_id } = await req.json();
    if (![razorpay_order_id, razorpay_payment_id, razorpay_signature, course_id].every((v) => typeof v === "string" && v.length > 0)) {
      return Response.json({ error: "Missing payment verification fields" }, { status: 400, headers: cors });
    }

    const admin = createClient(url, serviceKey);
    const { data: pending } = await admin.from("course_payments").select("*")
      .eq("provider_order_id", razorpay_order_id).eq("student_id", user.id).eq("course_id", course_id).eq("status", "pending").single();
    if (!pending) return Response.json({ error: "Pending order not found" }, { status: 404, headers: cors });

    const expected = await hmacHex(keySecret, `${razorpay_order_id}|${razorpay_payment_id}`);
    if (!constantTimeEqual(expected, razorpay_signature)) return Response.json({ error: "Invalid payment signature" }, { status: 400, headers: cors });

    const basic = btoa(`${keyId}:${keySecret}`);
    const paymentResponse = await fetch(`https://api.razorpay.com/v1/payments/${encodeURIComponent(razorpay_payment_id)}`, {
      headers: { "Authorization": `Basic ${basic}` },
    });
    const payment = await paymentResponse.json();
    if (!paymentResponse.ok || payment.status !== "captured" || payment.order_id !== razorpay_order_id ||
        Number(payment.amount) !== Number(pending.amount_paise) || payment.currency !== "INR") {
      return Response.json({ error: "Payment is not captured or does not match this order" }, { status: 400, headers: cors });
    }

    const { error: updateError } = await admin.from("course_payments").update({
      provider_payment_id: razorpay_payment_id, status: "paid", verified_at: new Date().toISOString(),
    }).eq("id", pending.id).eq("status", "pending");
    if (updateError) return Response.json({ error: "Could not record verified payment" }, { status: 500, headers: cors });

    await admin.from("enrollments").upsert({ student_id: user.id, course_id }, { onConflict: "student_id,course_id", ignoreDuplicates: true });
    return Response.json({ verified: true, course_id }, { headers: cors });
  } catch (_error) {
    return Response.json({ error: "Unexpected verification error" }, { status: 500, headers: cors });
  }
});
