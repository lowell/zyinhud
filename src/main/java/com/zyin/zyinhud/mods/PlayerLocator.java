package com.zyin.zyinhud.mods;

import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.passive.*;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.zyin.zyinhud.ZyinHUDRenderer;
import com.zyin.zyinhud.util.Localization;

/**
 * The Player Locator checks for nearby players and displays their name on screen wherever they are.
 */
public class PlayerLocator extends ZyinHUDModBase
{
	/** Enables/Disables this Mod */
	public static boolean Enabled;

    /**
     * Toggles this Mod on or off
     * @return The state the Mod was changed to
     */
    public static boolean ToggleEnabled()
    {
    	return Enabled = !Enabled;
    }

	/** The current mode for this mod */
	public static Modes Mode;

	/** The enum for the different types of Modes this mod can have */
    public static enum Modes
    {
        OFF(Localization.get("playerlocator.mode.off")),
        ON(Localization.get("playerlocator.mode.on"));

        private String friendlyName;

        private Modes(String friendlyName)
        {
        	this.friendlyName = friendlyName;
        }

        /**
         * Sets the next availble mode for this mod
         */
        public static Modes ToggleMode()
        {
        	return Mode = Mode.ordinal() < Modes.values().length - 1 ? Modes.values()[Mode.ordinal() + 1] : Modes.values()[0];
        }

        /**
         * Gets the mode based on its internal name as written in the enum declaration
         * @param modeName
         * @return
         */
        public static Modes GetMode(String modeName)
        {
        	try {return Modes.valueOf(modeName);}
        	catch (IllegalArgumentException e) {return values()[0];}
        }

        public String GetFriendlyName()
        {
        	return friendlyName;
        }
    }

    /** Shows how far you are from other players next to their name */
    public static boolean ShowDistanceToPlayers;
    public static boolean ShowPlayerHealth;
    public static boolean ShowWitherSkeletons;
    public static boolean ShowWolves;
    public static boolean UseWolfColors;

    private static final ResourceLocation iconsResourceLocation = new ResourceLocation("textures/gui/icons.png");

    private static final double pi = Math.PI;

    private static final String wolfName = Localization.get("entity.Wolf.name");
    private static final String sprintingMessagePrefix = "";
    private static final String sneakingMessagePrefix = TextFormatting.ITALIC.toString();
    private static final String ridingMessagePrefix = "    ";	//space for the saddle/minecart/boat/horse armor icon

    /** Don't render players that are closer than this */
    public static int viewDistanceCutoff = 0;
    public static final int minViewDistanceCutoff = 0;
    public static final int maxViewDistanceCutoff = 130;	//realistic max distance the game will render entities: up to ~115 blocks away

    public static int numOverlaysRendered;
    public static final int maxNumberOfOverlays = 50;	//render only the first nearest 50 players


    /**
     * Renders nearby players's names on the screen.
     * @param entity
     * @param x location on the HUD
     * @param y location on the HUD
     */
    public static void RenderEntityInfoOnHUD(Entity entity, int x, int y)
    {
    	if(numOverlaysRendered > maxNumberOfOverlays)
    		return;

    	if (!(entity instanceof EntityOtherPlayerMP ||
        	  entity instanceof EntityWolf ||
        	  (entity instanceof EntitySkeleton) && ((EntitySkeleton)entity).getSkeletonType() == 1))
        {
            return;    //we only care about other players and wolves
        }

        //if the player is in the world
        //and not looking at a menu
        //and F3 not pressed
        if (PlayerLocator.Enabled && Mode == Modes.ON &&
                (mc.inGameHasFocus || mc.currentScreen == null || mc.currentScreen instanceof GuiChat)
                && !mc.gameSettings.showDebugInfo)
        {

            //only show entities that are close by
            float distanceFromMe = mc.thePlayer.getDistanceToEntity(entity);

            if (distanceFromMe > maxViewDistanceCutoff
                    || distanceFromMe < viewDistanceCutoff
                    || distanceFromMe == 0) //don't render ourself!
            {
                return;
            }

        	String overlayMessage = "";
            int rgb = 0xFFFFFF;
            //calculate the color of the overlayMessage based on the distance from me
            int alpha = (int)(0x55 + 0xAA * ((maxViewDistanceCutoff - distanceFromMe) / maxViewDistanceCutoff));

            if(entity instanceof EntityOtherPlayerMP)
        	{
        		overlayMessage = GetOverlayMessageForOtherPlayer((EntityOtherPlayerMP)entity, distanceFromMe);

        		//format the string to be the same color as that persons team color
        		ScorePlayerTeam team = (ScorePlayerTeam)((EntityOtherPlayerMP)entity).getTeam();
        		if(team != null)
        			overlayMessage = team.formatString(overlayMessage);
        	}
        	else if(entity instanceof EntityWolf)
        	{
        		if(!ShowWolves || !PlayerIsWolfsOwner((EntityWolf)entity))
        			return;

        		overlayMessage = GetOverlayMessageForWolf((EntityWolf)entity, distanceFromMe);

        		if(UseWolfColors)
        		{
        			EnumDyeColor collarColor = ((EntityWolf)entity).getCollarColor();
        			float[] dyeRGBColors = EntitySheep.getDyeRgb(collarColor);	//func_175513_a() friendly name is probably "getHexColorsFromDye"

	                int r = (int)(dyeRGBColors[0]*255);
	                int g = (int)(dyeRGBColors[1]*255);
	                int b = (int)(dyeRGBColors[2]*255);
	                rgb = (r << 4*4) + (g << 4*2) + b;	//actual collar color

	                r = (0xFF - r)/2;
	                g = (0xFF - g)/2;
	                b = (0xFF - b)/2;
	                rgb = rgb + ((r << 4*4) + (g << 4*2) + b);	//a more white version of the collar color
        		}
        	}
        	else if(entity instanceof EntitySkeleton && (((EntitySkeleton)entity).getSkeletonType() == 1))
        	{
        		if(!ShowWitherSkeletons)
        			return;

        		overlayMessage = GetOverlayMessageForWitherSkeleton((EntitySkeleton)entity, distanceFromMe);

        		rgb = 0x555555;
        		alpha = alpha / 6;
        	}

        	if(entity.getRidingEntity() != null)
        		overlayMessage = "    " + overlayMessage;	//make room for any icons we render

            int overlayMessageWidth = mc.fontRendererObj.getStringWidth(overlayMessage);	//the width in pixels of the message
            ScaledResolution res = new ScaledResolution(mc);
            int width = res.getScaledWidth();		//~427
            int height = res.getScaledHeight();		//~240

            //center the text horizontally over the entity
            x -= overlayMessageWidth/2;

            //check if the text is attempting to render outside of the screen, and if so, fix it to snap to the edge of the screen.
            x = (x > width - overlayMessageWidth) ? width - overlayMessageWidth : x;
            x = (x < 0) ? 0 : x;
            y = (y > height - 10 && !ShowPlayerHealth) ? height - 10 : y;
            y = (y > height - 20 && ShowPlayerHealth) ? height - 20 : y;
            if(y < 10 && InfoLine.infoLineLocY <= 1 &&
            	(x > InfoLine.infoLineLocX + mc.fontRendererObj.getStringWidth(InfoLine.infoLineMessage) || x < InfoLine.infoLineLocX - overlayMessageWidth))
            	y = (y < 0) ? 0 : y;	//if the text is to the right or left of the info line then allow it to render in that open space
            else
            	y = (y < 10) ? 10 : y;	//use 10 instead of 0 so that we don't write text onto the top left InfoLine message area


            //render the overlay message
            GL11.glDisable(GL11.GL_LIGHTING);
    		GL11.glEnable(GL11.GL_BLEND);
    		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            int color = (alpha << 24) + rgb;	//alpha:r:g:b, (alpha << 24) turns it into the format: 0x##000000
            mc.fontRendererObj.drawStringWithShadow(overlayMessage, x, y, color);

            //also render whatever the player is currently riding on
            if (entity.getRidingEntity() instanceof EntityHorse)
            {
                EntityHorse horse = (EntityHorse)entity.getRidingEntity();
                HorseType armorType = horse.func_184783_dl();
                switch (armorType) {
                    case IRON:
                        RenderHorseArmorIronIcon(x, y);
                        break;
                    case GOLD:
                        RenderHorseArmorGoldIcon(x, y);
                        break;
                    case DIAMOND:
                        RenderHorseArmorDiamondIcon(x, y);
                        break;
                    case NONE:
                        if(horse.isHorseSaddled()) { RenderSaddleIcon(x, y); }
                        break;
                }
            }
            if (entity.getRidingEntity() instanceof EntityPig)
            {
            	RenderSaddleIcon(x, y);
            }
            else if (entity.getRidingEntity() instanceof EntityMinecart)
            {
            	RenderMinecartIcon(x, y);
            }
            else if (entity.getRidingEntity() instanceof EntityBoat)
            {
            	RenderBoatIcon(x, y);
            }

            //if showing player health is turned on, render the hp and a heart icon under their name
            if(ShowPlayerHealth && !(entity instanceof EntityMob))	//but don't show health for mobs, such as Wither Skeletons
            {
                int numHearts = (int)((((EntityLivingBase)entity).getHealth()+1) / 2);
            	String hpOverlayMessage = numHearts + "";

                int hpOverlayMessageWidth = mc.fontRendererObj.getStringWidth(hpOverlayMessage);
                int offsetX = (overlayMessageWidth - hpOverlayMessageWidth - 9) / 2;

                mc.fontRendererObj.drawStringWithShadow(hpOverlayMessage, x+offsetX, y+10, (alpha << 24) + 0xFFFFFF);

                GL11.glColor4f(1f, 1f, 1f, ((float)alpha) / 0xFF);
                ZyinHUDRenderer.RenderCustomTexture(x + offsetX + hpOverlayMessageWidth + 1, y + 9, 16, 0, 9, 9, iconsResourceLocation, 1f);	//black outline of the heart icon
                ZyinHUDRenderer.RenderCustomTexture(x + offsetX + hpOverlayMessageWidth + 1, y + 9, 52, 0, 9, 9, iconsResourceLocation, 1f);	//red interior of the heart icon
                GL11.glColor4f(1f, 1f, 1f, 1f);
            }

    		numOverlaysRendered++;
        }
    }


    private static boolean PlayerIsWolfsOwner(EntityWolf wolf)
    {
    	return wolf.isOnSameTeam(mc.thePlayer);
    }


	private static String GetOverlayMessageForWitherSkeleton(EntitySkeleton witherSkeleton, float distanceFromMe)
	{
		String overlayMessage = "Wither ";// + witherSkeleton.getCommandSenderName();
        // TODO Investigate getCommandSenderName()'s necessity

        //add distance to this wither skeleton into the message
        if (ShowDistanceToPlayers)
        {
        	overlayMessage = TextFormatting.GRAY + "[" + (int)distanceFromMe + "] " + TextFormatting.RESET + overlayMessage;
        }

        return overlayMessage;
	}

	private static String GetOverlayMessageForWolf(EntityWolf wolf, float distanceFromMe)
	{
		String overlayMessage;

		if(wolf.getCustomNameTag().isEmpty())
			overlayMessage = wolfName;
		else
			overlayMessage = wolf.getCustomNameTag();

        //add distance to this wolf into the message
        if (ShowDistanceToPlayers)
        {
        	overlayMessage = TextFormatting.GRAY + "[" + (int)distanceFromMe + "] " + TextFormatting.RESET + overlayMessage;
        }

        return overlayMessage;
	}


	private static String GetOverlayMessageForOtherPlayer(EntityOtherPlayerMP otherPlayer, float distanceFromMe)
	{
            String overlayMessage = otherPlayer.getDisplayNameString();

            //add distance to this player into the message
            if (ShowDistanceToPlayers)
            {
                //overlayMessage = "[" + (int)distanceFromMe + "] " + overlayMessage;
            	overlayMessage = TextFormatting.GRAY + "[" + (int)distanceFromMe + "] " + TextFormatting.RESET + overlayMessage;
            }

            //add special effects based on what the other player is doing
            if (otherPlayer.isSprinting())
            {
                overlayMessage = sprintingMessagePrefix + overlayMessage;	//nothing
            }
            if (otherPlayer.isSneaking())
            {
                overlayMessage = sneakingMessagePrefix + overlayMessage;	//italics
            }
            if (otherPlayer.isRiding())	//this doesn't work on some servers
            {
                overlayMessage = ridingMessagePrefix + overlayMessage;		//space for the saddle and horse armor icons
            }

            return overlayMessage;
	}

	private static void RenderBoatIcon(int x, int y)
	{
		itemRenderer.renderItemIntoGUI(new ItemStack(Items.boat), x, y - 4);
		GL11.glDisable(GL11.GL_LIGHTING);
	}
	private static void RenderMinecartIcon(int x, int y)
	{
		itemRenderer.renderItemIntoGUI(new ItemStack(Items.minecart), x, y - 4);
		GL11.glDisable(GL11.GL_LIGHTING);
	}
	private static void RenderHorseArmorDiamondIcon(int x, int y)
	{
		itemRenderer.renderItemIntoGUI( new ItemStack(Items.diamond_horse_armor), x, y - 4);
		GL11.glDisable(GL11.GL_LIGHTING);
	}
	private static void RenderHorseArmorGoldIcon(int x, int y)
	{
		itemRenderer.renderItemIntoGUI(new ItemStack(Items.golden_horse_armor), x, y - 4);
		GL11.glDisable(GL11.GL_LIGHTING);
	}
	private static void RenderHorseArmorIronIcon(int x, int y)
	{
		itemRenderer.renderItemIntoGUI(new ItemStack(Items.iron_horse_armor), x, y - 4);
		GL11.glDisable(GL11.GL_LIGHTING);
	}
	private static void RenderSaddleIcon(int x, int y)
	{
		itemRenderer.renderItemIntoGUI(new ItemStack(Items.saddle), x, y - 4);
		GL11.glDisable(GL11.GL_LIGHTING);
	}




    /*
    public static double AngleBetweenTwoVectors(Vec3 a, Vec3 b)
    {
        return Math.acos(a.dotProduct(b) / (a.lengthVector() * b.lengthVector()));
    }
    public static double SignedAngleBetweenTwoVectors(Vec3 a, Vec3 b)
    {
    	// Get the angle in degrees between 0 and 180
    	double angle = AngleBetweenTwoVectors(b, a);

    	// the vector perpendicular to referenceForward (90 degrees clockwise)
    	// (used to determine if angle is positive or negative)
    	Vec3 referenceRight = (Vec3.createVectorHelper(0, 1, 0)).crossProduct(a);

    	// Determine if the degree value should be negative.  Here, a positive value
    	// from the dot product means that our vector is the right of the reference vector
    	// whereas a negative value means we're on the left.
    	double sign = (b.dotProduct(referenceRight) > 0.0) ? 1.0: -1.0;

    	return sign * angle;
    }
    */




    /**
     * Gets the status of the Player Locator
     * @return the string "players" if the Player Locator is enabled, otherwise "".
     */
    public static String CalculateMessageForInfoLine()
    {
        if (Mode == Modes.OFF || !PlayerLocator.Enabled)
        {
            return "";
        }
        else if (Mode == Modes.ON)
        {
            return TextFormatting.WHITE + Localization.get("playerlocator.infoline");
        }
        else
        {
            return TextFormatting.WHITE + "???";
        }
    }

    /**
     * Toggle showing the distance to players
     * @return The new Clock mode
     */
    public static boolean ToggleShowDistanceToPlayers()
    {
    	return ShowDistanceToPlayers = !ShowDistanceToPlayers;
    }

    /**
     * Toggle showing the players health
     * @return The new Clock mode
     */
    public static boolean ToggleShowPlayerHealth()
    {
    	return ShowPlayerHealth = !ShowPlayerHealth;
    }

    /**
     * Toggle showing wolves in addition to other players
     * @return The new Clock mode
     */
    public static boolean ToggleShowWolves()
    {
    	return ShowWolves = !ShowWolves;
    }

    /**
     * Toggle using the coler of the wolf's collar to colorize the wolf's name
     * @return The new Clock mode
     */
    public static boolean ToggleUseWolfColors()
    {
    	return UseWolfColors = !UseWolfColors;
    }

    /**
     * Toggle showing wolves in addition to other players
     * @return The new Clock mode
     */
    public static boolean ToggleShowWitherSkeletons()
    {
    	return ShowWitherSkeletons = !ShowWitherSkeletons;
    }

}
