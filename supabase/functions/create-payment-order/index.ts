import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return Response.json({ error: "Method not allowed" }, { status: 405, headers: cors });

  try {
    const url = Deno.env.get("SUPABASE_URL")!;
    const anon = Deno.env.get("SUPABASE_ANON_KEY")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const razorpayKey = Deno.env.get("RAZORPAY_KEY_ID")!;
    const razorpaySecret = Deno.env.get("RAZORPAY_KEY_SECRET")!;
    const auth = req.headers.get("Authorization") ?? "";
    if (!auth.startsWith("Bearer ")) return Response.json({ error: "Unauthorized" }, { status: 401, headers: cors });

    const userClient = createClient(url, anon, { global: { headers: { Authorization: auth } } });
    const { data: { user }, error: userError } = await userClient.auth.getUser();
    if (userError || !user) return Response.json({ error: "Unauthorized" }, { status: 401, headers: cors });

    const { course_id } = await req.json();
    if (typeof course_id !== "string") return Response.json({ error: "course_id is required" }, { status: 400, headers: cors });

    const admin = createClient(url, serviceKey);
    const { data: course, error: courseError } = await admin.from("courses")
      .select("id,title,price,is_free,published").eq("id", course_id).eq("published", true).single();
    if (courseError || !course || course.is_free) return Response.json({ error: "Paid published course not found" }, { status: 404, headers: cors });

    const amount = Math.round(Number(course.price) * 100);
    if (!Number.isSafeInteger(amount) || amount <= 0) return Response.json({ error: "Invalid course price" }, { status: 400, headers: cors });

    const basic = btoa(`${razorpayKey}:${razorpaySecret}`);
    const orderResponse = await fetch("https://api.razorpay.com/v1/orders", {
      method: "POST",
      headers: { "Authorization": `Basic ${basic}`, "Content-Type": "application/json" },
      body: JSON.stringify({ amount, currency: "INR", receipt: `pt_${crypto.randomUUID().replaceAll("-", "").slice(0, 24)}`, notes: { course_id, student_id: user.id } }),
    });
    const order = await orderResponse.json();
    if (!orderResponse.ok) return Response.json({ error: "Payment provider could not create order" }, { status: 502, headers: cors });

    const { error: insertError } = await admin.from("course_payments").insert({
      student_id: user.id, course_id, provider: "razorpay", provider_order_id: order.id,
      amount_paise: amount, currency: "INR", status: "pending",
    });
    if (insertError) return Response.json({ error: "Could not save payment order" }, { status: 500, headers: cors });

    return Response.json({ order_id: order.id, amount, currency: "INR", key_id: razorpayKey, course_title: course.title }, { headers: cors });
  } catch (_error) {
    return Response.json({ error: "Unexpected payment setup error" }, { status: 500, headers: cors });
  }
});
