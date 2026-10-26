package tv.widgetv.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * The single focus indicator: a 3px gold-400 ring with a 2px plaster outer ring (no fill) that springs
 * (overshoot) from one target rect to the next. Coordinates are in this view's parent space. Hidden
 * (faded) where the focused element draws its own gold state: pills, dropdown and panel rows.
 */
public final class GlassHighlight extends View {
    private final RectF from = new RectF();
    private final RectF to = new RectF();
    private final RectF cur = new RectF();
    private final RectF edge = new RectF();
    private float fromR, toR, curR;
    private boolean shown;
    private final Paint gold = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float goldW, outerW;
    private boolean visible = true;
    private final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);

    public GlassHighlight(Context c, AttributeSet a) {
        super(c, a);
        float d = getResources().getDisplayMetrics().density;
        goldW = 1.5f * d;  // 3px at 1080p
        outerW = 1f * d;   // 2px
        gold.setStyle(Paint.Style.STROKE);
        gold.setStrokeWidth(goldW);
        gold.setColor(Lapis.GOLD_400);
        outer.setStyle(Paint.Style.STROKE);
        outer.setStrokeWidth(outerW);
        outer.setColor(Lapis.PLASTER_100);
        anim.setDuration(380);
        anim.setInterpolator(new OvershootInterpolator(1.3f));
        anim.addUpdateListener(va -> {
            float t = (float) va.getAnimatedValue();
            cur.set(lerp(from.left, to.left, t), lerp(from.top, to.top, t),
                    lerp(from.right, to.right, t), lerp(from.bottom, to.bottom, t));
            curR = Math.max(0f, lerp(fromR, toR, t));
            invalidate();
        });
    }

    /** Move to target; no-op if already heading there. Jumps (no animation) on first show. */
    void moveTo(RectF target, float radius, boolean animate) {
        if (shown && target.equals(to) && radius == toR) return;
        anim.cancel();
        to.set(target);
        toR = radius;
        if (!shown || !animate) {
            cur.set(target);
            curR = radius;
            shown = true;
            invalidate();
            return;
        }
        from.set(cur);
        fromR = curR;
        anim.start();
    }

    /** Fade the ring in or out; it keeps tracking its target either way. */
    void setRingVisible(boolean on) {
        if (on == visible) return;
        visible = on;
        animate().cancel();
        animate().alpha(on ? 1f : 0f).setDuration(160).start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!shown) return;
        // The target rect is the ring's inner edge: gold just outside it, plaster just outside the gold.
        edge.set(cur);
        edge.inset(-goldW / 2, -goldW / 2);
        canvas.drawRoundRect(edge, curR + goldW / 2, curR + goldW / 2, gold);
        edge.inset(-(goldW + outerW) / 2, -(goldW + outerW) / 2);
        float r = curR + goldW + outerW / 2;
        canvas.drawRoundRect(edge, r, r, outer);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
