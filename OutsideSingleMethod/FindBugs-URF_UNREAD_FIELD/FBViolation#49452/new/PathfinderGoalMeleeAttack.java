package net.minecraft.server;

import org.bukkit.event.entity.EntityTargetEvent; // CraftBukkit

public class PathfinderGoalMeleeAttack extends PathfinderGoal {

    World a;
    EntityCreature b;
    int c;
    double d;
    boolean e;
    PathEntity f;
    Class g;
    private int h;
    private double i;
    private double j;
    private double k;

    // Poweruser start
    private int failedSearches = 0;
    private boolean targetFound = false;
    private double originalSearchRange;
    private double lastAdjustedRange;
    // Poweruser end

    public PathfinderGoalMeleeAttack(EntityCreature entitycreature, Class oclass, double d0, boolean flag) {
        this(entitycreature, d0, flag);
        this.g = oclass;
    }

    public PathfinderGoalMeleeAttack(EntityCreature entitycreature, double d0, boolean flag) {
        this.b = entitycreature;
        this.a = entitycreature.world;
        this.d = d0;
        this.e = flag;
        this.a(3);
        // Poweruser start
        this.originalSearchRange = this.b.getAttributeInstance(GenericAttributes.b).getValue();
        this.lastAdjustedRange = this.originalSearchRange;
        // Poweruser end
    }

    public boolean a() {
        EntityLiving entityliving = this.b.getGoalTarget();
        if (entityliving == null) {
            return false;
        } else if (!entityliving.isAlive()) {
            return false;
        } else if (this.g != null && !this.g.isAssignableFrom(entityliving.getClass())) {
            return false;
        } else {
            // Poweruser start
            boolean out = false;
            double xdiff = entityliving.locX - this.b.locX;
            double zdiff = entityliving.locZ - this.b.locZ;
            if(!entityliving.onGround || (xdiff * xdiff + zdiff * zdiff) < 9.0D) {
                out = true;
            } else {
                AttributeInstance attr = this.b.getAttributeInstance(GenericAttributes.b);
                attr.setValue(this.lastAdjustedRange);
                this.f = this.b.getNavigation().a(entityliving);
                attr.setValue(this.originalSearchRange);
                out = this.f != null;
            }
            return out;
            // Poweruser end
        }
    }

    public boolean b() {
        EntityLiving entityliving = this.b.getGoalTarget();

        // CraftBukkit start
        EntityTargetEvent.TargetReason reason = this.b.getGoalTarget() == null ? EntityTargetEvent.TargetReason.FORGOT_TARGET : EntityTargetEvent.TargetReason.TARGET_DIED;
        if (this.b.getGoalTarget() == null || (this.b.getGoalTarget() != null && !this.b.getGoalTarget().isAlive())) {
            org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTargetEvent(b, null, reason);
        }
        // CraftBukkit end

        return entityliving == null ? false : (!entityliving.isAlive() ? false : (!this.e ? !this.b.getNavigation().g() : this.b.b(MathHelper.floor(entityliving.locX), MathHelper.floor(entityliving.locY), MathHelper.floor(entityliving.locZ))));
    }

    public void c() {
        this.b.getNavigation().a(this.f, this.d);
        this.h = 0;
    }

    public void d() {
        this.b.getNavigation().h();
    }

    public void e() {
        EntityLiving entityliving = this.b.getGoalTarget();

        this.b.getControllerLook().a(entityliving, 30.0F, 30.0F);
        double d0 = this.b.e(entityliving.locX, entityliving.boundingBox.b, entityliving.locZ);
        double d1 = (double) (this.b.width * 2.0F * this.b.width * 2.0F + entityliving.width);

        --this.h;
        if ((this.e || this.b.getEntitySenses().canSee(entityliving)) && this.h <= 0 && (this.i == 0.0D && this.j == 0.0D && this.k == 0.0D || entityliving.e(this.i, this.j, this.k) >= 1.0D || this.b.aI().nextFloat() < 0.05F)) {
            this.i = entityliving.locX;
            this.j = entityliving.boundingBox.b;
            this.k = entityliving.locZ;
            this.h = 4 + this.b.aI().nextInt(7);

            if (d0 > 1024.0D) {
                this.h += 10;
            } else if (d0 > 256.0D) {
                this.h += 5;
            }

            /*
            if (!this.b.getNavigation().a((Entity) entityliving, this.d)) {
                this.h += 15;
            }
            */

            // Poweruser start
            AttributeInstance attr = this.b.getAttributeInstance(GenericAttributes.b);
            double newRange = this.originalSearchRange;
            if(this.failedSearches > 0) {
                double currentRange = attr.getValue();
                double xdiff = Math.abs(entityliving.locX - this.b.locX);
                double zdiff = Math.abs(entityliving.locZ - this.b.locZ);
                newRange = Math.min(Math.max(xdiff, zdiff) + 4.0D, currentRange);
            }
            this.lastAdjustedRange = newRange;
            attr.setValue(newRange);
            this.targetFound = this.b.getNavigation().a((Entity) entityliving, this.d);
            attr.setValue(this.originalSearchRange);

            if (!this.targetFound) {
                this.h += 15;
                this.failedSearches++;
            }
            if(this.targetFound || this.failedSearches >= 10) {
                this.failedSearches = 0;
            }

            this.h += 2 * this.failedSearches;
            // Poweruser end
        }

        this.c = Math.max(this.c - 1, 0);
        if (d0 <= d1 && this.c <= 20) {
            this.c = 20;
            if (this.b.be() != null) {
                this.b.ba();
            }

            this.b.m(entityliving);
        }
    }
}
