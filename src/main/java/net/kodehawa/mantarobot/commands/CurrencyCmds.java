package net.kodehawa.mantarobot.commands;

import com.rethinkdb.model.OptArgs;
import com.rethinkdb.net.Cursor;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.kodehawa.mantarobot.MantaroBot;
import net.kodehawa.mantarobot.MantaroShard;
import net.kodehawa.mantarobot.commands.currency.RateLimiter;
import net.kodehawa.mantarobot.commands.currency.TextChannelGround;
import net.kodehawa.mantarobot.commands.currency.item.Item;
import net.kodehawa.mantarobot.commands.currency.item.ItemStack;
import net.kodehawa.mantarobot.commands.currency.item.Items;
import net.kodehawa.mantarobot.core.listeners.operations.InteractiveOperation;
import net.kodehawa.mantarobot.core.listeners.operations.InteractiveOperations;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.data.entities.DBUser;
import net.kodehawa.mantarobot.data.entities.Player;
import net.kodehawa.mantarobot.data.entities.helpers.Inventory;
import net.kodehawa.mantarobot.data.entities.helpers.UserData;
import net.kodehawa.mantarobot.modules.Command;
import net.kodehawa.mantarobot.modules.CommandRegistry;
import net.kodehawa.mantarobot.modules.Module;
import net.kodehawa.mantarobot.modules.commands.SimpleCommand;
import net.kodehawa.mantarobot.modules.commands.base.Category;
import net.kodehawa.mantarobot.utils.Utils;
import net.kodehawa.mantarobot.utils.commands.EmoteReference;
import org.apache.commons.lang3.tuple.Pair;

import javax.xml.soap.Text;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.rethinkdb.RethinkDB.r;
import static net.kodehawa.mantarobot.utils.StringUtils.SPLIT_PATTERN;

@Module
public class CurrencyCmds {

    private static Random random = new Random();

    @Command
    public static void daily(CommandRegistry cr) {
        RateLimiter rateLimiter = new RateLimiter(TimeUnit.HOURS, 24);
        Random r = new Random();
        cr.register("daily", new SimpleCommand(Category.CURRENCY) {
            @Override
            public void call(GuildMessageReceivedEvent event, String content, String[] args) {
                String id = event.getAuthor().getId();
                long money = 15L;
                User mentionedUser = null;
                try {
                    mentionedUser = event.getMessage().getMentionedUsers().get(0);
                }
                catch (IndexOutOfBoundsException ignored) {
                }

                Player player;

                if (!rateLimiter.process(id)) {
                    event.getChannel().sendMessage(EmoteReference.STOPWATCH +
                            "Halt! You can only do this once every 24 hours.\n**You'll be able to use this command again in " +
                            Utils.getVerboseTime(Math.abs(System.currentTimeMillis() - rateLimiter.getUsersRateLimited().get(id)))
                            + ".**").queue();
                    return;
                }

                if (mentionedUser != null && !mentionedUser.getId().equals(event.getAuthor().getId())) {
                    money = money + r.nextInt(2);
                    player = MantaroData.db().getPlayer(event.getGuild().getMember(mentionedUser));

                    if (player.getInventory().containsItem(Items.COMPANION)) money = Math.round(money + (money * 0.10));

                    if (mentionedUser.getId().equals(player.getData().getMarriedWith()) && player.getData().getMarriedSince() != null &&
                            Long.parseLong(player.getData().anniversary()) - player.getData().getMarriedSince() > TimeUnit.DAYS.toMillis(1))
                    {
                        money = money + r.nextInt(10);

                        if (player.getInventory().containsItem(Items.RING_2)) {
                            money = money + r.nextInt(5);
                        }
                    }

                    player.addMoney(money);
                    player.save();

                    event.getChannel().sendMessage(EmoteReference.CORRECT + "I gave your **$" + money + "** daily credits to " +
                            mentionedUser.getName()).queue();
                    return;
                }

                player = MantaroData.db().getPlayer(event.getMember());

                player.addMoney(money);
                player.save();
                event.getChannel().sendMessage(EmoteReference.CORRECT + "You got **$" + money + "** daily credits.").queue();
            }

            @Override
            public MessageEmbed help(GuildMessageReceivedEvent event) {
                return helpEmbed(event, "Daily command")
                        .setDescription("**Gives you $300 credits per day (or between 300 and 350 if you transfer it to another person)**.")
                        .build();
            }
        });
    }

    @Command
    public static void gamble(CommandRegistry cr) {
        RateLimiter rateLimiter = new RateLimiter(TimeUnit.SECONDS, 15);
        Random r = new Random();

        cr.register("gamble", new SimpleCommand(Category.CURRENCY) {
            @Override
            public void call(GuildMessageReceivedEvent event, String content, String[] args) {
                String id = event.getAuthor().getId();
                Player player = MantaroData.db().getPlayer(event.getMember());

                if (!rateLimiter.process(id)) {
                    event.getChannel().sendMessage(EmoteReference.STOPWATCH +
                            "Halt! You're gambling so fast that I can't print enough money!").queue();
                    return;
                }

                if (player.getMoney() <= 0) {
                    event.getChannel().sendMessage(EmoteReference.ERROR2 + "You're broke. Search for some credits first!").queue();
                    return;
                }

                if(player.getMoney() > (long)(Integer.MAX_VALUE) * 2){
                    event.getChannel().sendMessage(EmoteReference.ERROR2 + "You have too much money! Maybe transfer or buy items?").queue();
                    return;
                }

                double multiplier;
                long i;
                int luck;
                try {
                    switch (content) {
                        case "all":
                        case "everything":
                            i = player.getMoney();
                            multiplier = 1.4d + (r.nextInt(1500) / 1000d);
                            luck = 30 + (int) (multiplier * 10) + r.nextInt(20);
                            break;
                        case "half":
                            i = player.getMoney() == 1 ? 1 : player.getMoney() / 2;
                            multiplier = 1.2d + (r.nextInt(1500) / 1000d);
                            luck = 20 + (int) (multiplier * 15) + r.nextInt(20);
                            break;
                        case "quarter":
                            i = player.getMoney() == 1 ? 1 : player.getMoney() / 4;
                            multiplier = 1.1d + (r.nextInt(1100) / 1000d);
                            luck = 25 + (int) (multiplier * 10) + r.nextInt(18);
                            break;
                        default:
                            i = Long.parseLong(content);
                            if (i > player.getMoney() || i < 0) throw new UnsupportedOperationException();
                            multiplier = 1.1d + (i / player.getMoney() * r.nextInt(1300) / 1000d);
                            luck = 15 + (int) (multiplier * 15) + r.nextInt(10);
                            break;
                    }
                }
                catch (NumberFormatException e) {
                    event.getChannel().sendMessage(EmoteReference.ERROR2 + "Please type a valid number equal or less than your credits or" +
                            " `all` to gamble all your credits.").queue();
                    return;
                }
                catch (UnsupportedOperationException e) {
                    event.getChannel().sendMessage(EmoteReference.ERROR2 + "Please type a value within your credits amount.").queue();
                    return;
                }

                if (player.getInventory().containsItem(Items.ENHANCER)) {
                    luck = luck + 5;
                }

                User user = event.getAuthor();
                long gains = (long) (i * multiplier);
                gains = Math.round(gains * 0.55);

                final int finalLuck = luck;
                final long finalGains = gains;

                if (i >= Integer.MAX_VALUE / 4) {
                    event.getChannel().sendMessage(EmoteReference.WARNING + "You're about to bet **" + i + "** " +
                            "credits (which seems to be a lot). Are you sure? Type **yes** to continue and **no** otherwise.").queue();
                    InteractiveOperations.create(event.getChannel(), "Gambling",
                            (int) TimeUnit.SECONDS.toMillis(30), OptionalInt.empty(), new InteractiveOperation() {
                                @Override
                                public boolean run(GuildMessageReceivedEvent e) {
                                    if (e.getAuthor().getId().equals(user.getId())) {
                                        if (e.getMessage().getContent().equalsIgnoreCase("yes")) {
                                            proceedGamble(event, player, finalLuck, random, i, finalGains);
                                            return true;
                                        }
                                        else if (e.getMessage().getContent().equalsIgnoreCase("no")) {
                                            e.getChannel().sendMessage(EmoteReference.ZAP + "Cancelled bet.").queue();
                                            return true;
                                        }
                                    }

                                    return false;
                                }

                                @Override
                                public void onExpire() {
                                    event.getChannel().sendMessage(EmoteReference.ERROR + "Time to complete the operation has ran out.")
                                            .queue();
                                }
                            });

                    return;
                }

                proceedGamble(event, player, luck, random, i, gains);
            }

            @Override
            public MessageEmbed help(GuildMessageReceivedEvent event) {
                return helpEmbed(event, "Gamble command")
                        .setDescription("Gambles your money")
                        .addField("Usage", "~>gamble <all/half/quarter> or ~>gamble <amount>", false)
                        .build();
            }
        });
    }

    @Command
    public static void inventory(CommandRegistry cr) {
        cr.register("inventory", new SimpleCommand(Category.CURRENCY) {
            @Override
            public void call(GuildMessageReceivedEvent event, String content, String[] args) {
                Player user = MantaroData.db().getPlayer(event.getMember());

                EmbedBuilder builder = baseEmbed(event, event.getMember().getEffectiveName() + "'s Inventory", event.getAuthor()
                        .getEffectiveAvatarUrl());
                List<ItemStack> list = user.getInventory().asList();
                if (list.isEmpty()) builder.setDescription("There is only dust.");
                else
                    user.getInventory().asList().forEach(stack -> {
                        long buyValue = stack.getItem().isBuyable() ? (long) (stack.getItem().getValue() * 1.1) : 0;
                        long sellValue = stack.getItem().isSellable() ? (long) (stack.getItem().getValue() * 0.9) : 0;
                        builder.addField(stack.getItem().getEmoji() + " " + stack.getItem().getName() + " x " + stack.getAmount(), String
                                        .format("**Price**: \uD83D\uDCE5 %d \uD83D\uDCE4 %d\n%s", buyValue, sellValue, stack.getItem()
                                                .getDesc())
                                , false);
                    });

                event.getChannel().sendMessage(builder.build()).queue();
            }

            @Override
            public MessageEmbed help(GuildMessageReceivedEvent event) {
                return helpEmbed(event, "Inventory command")
                        .setDescription("**Shows your current inventory.**")
                        .build();
            }
        });
    }

    @Command
    public static void loot(CommandRegistry cr) {
        RateLimiter rateLimiter = new RateLimiter(TimeUnit.MINUTES, 5);
        Random r = new Random();

        cr.register("loot", new SimpleCommand(Category.CURRENCY) {
            @Override
            public void call(GuildMessageReceivedEvent event, String content, String[] args) {
                String id = event.getAuthor().getId();
                Player player = MantaroData.db().getPlayer(event.getMember());

                if (!rateLimiter.process(id)) {
                    event.getChannel().sendMessage(EmoteReference.STOPWATCH +
                            "Cooldown a lil bit, you can only do this once every 5 minutes.\n **You'll be able to use this command again " +
                            "in " +
                            Utils.getVerboseTime(Math.abs(System.currentTimeMillis() - rateLimiter.getUsersRateLimited().get(id)))
                            + ".**").queue();
                    return;
                }

                TextChannelGround ground = TextChannelGround.of(event);

                if (r.nextInt(1500) == 0) {
                    TextChannelGround.of(event.getChannel()).dropItem(Items.LOOT_CRATE);
                }

                List<ItemStack> loot = ground.collectItems();
                int moneyFound = ground.collectMoney() + Math.max(0, r.nextInt(10) - 2);


                if (MantaroData.db().getUser(event.getMember()).isPremium() && moneyFound > 0) {
                    moneyFound = moneyFound + random.nextInt(moneyFound);
                }

                if (!loot.isEmpty()) {
                    String s = ItemStack.toString(ItemStack.reduce(loot));
                    player.getInventory().merge(loot);
                    if (moneyFound != 0) {
                        if (player.addMoney(moneyFound)) {
                            event.getChannel().sendMessage(EmoteReference.POPPER + "Digging through messages, you found " + s + ", along " +
                                    "with $" + moneyFound + " credits!").queue();
                        }
                        else {
                            event.getChannel().sendMessage(EmoteReference.POPPER + "Digging through messages, you found " + s + ", along " +
                                    "with $" + moneyFound + " credits. But you already had too many credits. Your bag overflowed" +
                                    ".\nCongratulations, you exploded a Java long. Here's a buggy money bag for you.").queue();
                        }
                    }
                    else {
                        event.getChannel().sendMessage(EmoteReference.MEGA + "Digging through messages, you found " + s).queue();
                    }
                }
                else {
                    if (moneyFound != 0) {
                        if (player.addMoney(moneyFound)) {
                            event.getChannel().sendMessage(EmoteReference.POPPER + "Digging through messages, you found **$" + moneyFound +
                                    " credits!**").queue();
                        }
                        else {
                            event.getChannel().sendMessage(EmoteReference.POPPER + "Digging through messages, you found $" + moneyFound +
                                    " credits. But you already had too many credits. Your bag overflowed.\nCongratulations, you exploded " +
                                    "a Java long. Here's a buggy money bag for you.").queue();
                        }
                    }
                    else {
                        event.getChannel().sendMessage(EmoteReference.SAD + "Digging through messages, you found nothing but dust").queue();
                    }
                }
                player.save();
            }

            @Override
            public MessageEmbed help(GuildMessageReceivedEvent event) {
                return helpEmbed(event, "Loot command")
                        .setDescription("**Loot the current chat for items, for usage in Mantaro's currency system.**\n"
                                + "Currently, there are ``" + Items.ALL.length + "`` items available in chance," +
                                "in which you have a `random chance` of getting one or more.")
                        .addField("Usage", "~>loot", false)
                        .build();
            }
        });
    }

    @Command
    public static void market(CommandRegistry cr) {
        cr.register("market", new SimpleCommand(Category.CURRENCY) {
            RateLimiter rateLimiter = new RateLimiter(TimeUnit.SECONDS, 5);

            @Override
            public void call(GuildMessageReceivedEvent event, String content, String[] args) {
                if (!rateLimiter.process(event.getAuthor().getId())) {
                    event.getChannel().sendMessage(EmoteReference.STOPWATCH +
                            "Wait! You're calling me so fast that I can't get enough items!").queue();
                    return;
                }

                Player player = MantaroData.db().getPlayer(event.getMember());

                if (args.length > 0) {
                    int itemNumber = 1;
                    String itemName = content.replace(args[0] + " ", "");
                    boolean isMassive = !itemName.isEmpty() && itemName.split(" ")[0].matches("^[0-9]*$");
                    if (isMassive) {
                        try {
                            itemNumber = Math.abs(Integer.valueOf(itemName.split(" ")[0]));
                            itemName = itemName.replace(args[1] + " ", "");
                        }
                        catch (Exception e) {
                            if (e instanceof NumberFormatException) {
                                event.getChannel().sendMessage(EmoteReference.ERROR + "Not a valid number of items to buy.").queue();
                            }
                        }
                    }

                    if (args[0].equals("sell")) {
                        try {
                            if (args[1].equals("all")) {
                                long all = player.getInventory().asList().stream()
                                        .filter(item -> item.getItem().isSellable())
                                        .mapToLong(value -> (long) (value.getItem().getValue() * value.getAmount() * 0.9d))
                                        .sum();

                                if (args.length > 2 && args[2].equals("calculate")) {
                                    event.getChannel().sendMessage(EmoteReference.THINKING + "You'll get **" + all + "** credits if you " +
                                            "sell all of your items").queue();
                                    return;
                                }

                                player.getInventory().clearOnlySellables();

                                if (player.addMoney(all)) {
                                    event.getChannel().sendMessage(EmoteReference.MONEY + "You sold all your inventory items and gained "
                                            + all + " credits!").queue();
                                }
                                else {
                                    event.getChannel().sendMessage(EmoteReference.MONEY + "You sold all your inventory items and gained "
                                            + all + " credits. But you already had too many credits. Your bag overflowed" +
                                            ".\nCongratulations, you exploded a Java long (how??). Here's a buggy money bag for you.")
                                            .queue();
                                }

                                player.saveAsync();
                                return;
                            }

                            Item toSell = Items.fromAny(itemName).orElse(null);

                            if (!toSell.isSellable()) {
                                event.getChannel().sendMessage(EmoteReference.ERROR + "You cannot sell an item that cannot be sold.")
                                        .queue();
                                return;
                            }

                            if (player.getInventory().asMap().getOrDefault(toSell, null) == null) {
                                event.getChannel().sendMessage(EmoteReference.STOP + "You cannot sell an item you don't have.").queue();
                                return;
                            }

                            if (player.getInventory().getAmount(toSell) < itemNumber) {
                                event.getChannel().sendMessage(EmoteReference.ERROR + "You cannot sell more items than what you have.")
                                        .queue();
                                return;
                            }

                            int many = itemNumber * -1;
                            long amount = Math.round((toSell.getValue() * 0.9)) * Math.abs(many);
                            player.getInventory().process(new ItemStack(toSell, many));

                            if (player.addMoney(amount)) {
                                event.getChannel().sendMessage(EmoteReference.CORRECT + "You sold " + Math.abs(many) + " **" + toSell
                                        .getName() +
                                        "** and gained " + amount + " credits!").queue();
                            }
                            else {
                                event.getChannel().sendMessage(EmoteReference.CORRECT + "You sold **" + toSell.getName() +
                                        "** and gained" + amount + " credits. But you already had too many credits. Your bag overflowed" +
                                        ".\nCongratulations, you exploded a Java long (how??). Here's a buggy money bag for you.").queue();
                            }

                            player.save();
                            return;
                        }
                        catch (Exception e) {
                            event.getChannel().sendMessage(EmoteReference.ERROR + "Item doesn't exist or invalid syntax").queue();
                            e.printStackTrace();
                        }
                        return;
                    }

                    if (args[0].equals("buy")) {
                        Item itemToBuy = Items.fromAny(itemName).orElse(null);

                        if (itemToBuy == null) {
                            event.getChannel().sendMessage(EmoteReference.ERROR + "You cannot buy an unexistant item.").queue();
                            return;
                        }

                        try {
                            if (!itemToBuy.isBuyable()) {
                                event.getChannel().sendMessage(EmoteReference.ERROR + "You cannot buy an item that cannot be bought.")
                                        .queue();
                                return;
                            }

                            if (player.getInventory().getAmount(itemToBuy) + itemNumber < 0) {
                                //assume overflow
                                event.getChannel().sendMessage(EmoteReference.ERROR + "You cannot buy more of that object!").queue();
                                return;
                            }

                            if (player.removeMoney(itemToBuy.getValue() * itemNumber)) {
                                player.getInventory().process(new ItemStack(itemToBuy, itemNumber));
                                player.save();
                                event.getChannel().sendMessage(EmoteReference.OK + "Bought " + itemNumber + " " + itemToBuy.getEmoji() +
                                        " successfully. You now have " + player.getMoney() + " credits.").queue();

                            }
                            else {
                                event.getChannel().sendMessage(EmoteReference.STOP + "You don't have enough money to buy this item.")
                                        .queue();
                            }
                            return;
                        }
                        catch (Exception e) {
                            event.getChannel().sendMessage(EmoteReference.ERROR + "Item doesn't exist or invalid syntax.").queue();
                        }
                        return;
                    }
                }

                EmbedBuilder embed = baseEmbed(event, EmoteReference.MARKET + "Mantaro Market");

                Stream.of(Items.ALL).forEach(item -> {
                    if (!item.isHidden()) {
                        String buyValue = item.isBuyable() ? EmoteReference.BUY + "$" + String.valueOf((int) Math.floor(item.getValue() *
                                1.1)) + " " : "";
                        String sellValue = item.isSellable() ? EmoteReference.SELL + "$" + String.valueOf((int) Math.floor(item.getValue
                                () * 0.9)) : "";
                        embed.addField(item.getEmoji() + " " + item.getName(), buyValue + sellValue, true);
                    }
                });

                event.getChannel().sendMessage(
                        embed.setThumbnail("https://i.imgur.com/OA7QCaM.png")
                                .build()).queue();
            }

            @Override
            public MessageEmbed help(GuildMessageReceivedEvent event) {
                return helpEmbed(event, "Mantaro's market")
                        .setDescription("**List current items for buying and selling.**")
                        .addField("Buying and selling", "To buy do ~>market buy <item emoji>. It will substract the value from your money" +
                                " and give you the item.\n" +
                                "To sell do `~>market sell all` to sell all your items or `~>market sell <item emoji>` to sell the " +
                                "specified item. " +
                                "**You'll get the sell value of the item on coins to spend.**", false)
                        .addField("To know", "If you don't have enough money you cannot buy the items.", false)
                        .addField("Information", "To buy and sell multiple items you need to do `~>market <buy/sell> <amount> <item>`",
                                false)
                        .build();
            }
        });
    }

    @Command
    public static void profile(CommandRegistry cr) {
        cr.register("profile", new SimpleCommand(Category.CURRENCY) {
            @Override
            public void call(GuildMessageReceivedEvent event, String content, String[] args) {

                Player player = MantaroData.db().getPlayer(event.getMember());
                DBUser u1 = MantaroData.db().getUser(event.getMember());
                User author = event.getAuthor();

                if (args.length > 0 && args[0].equals("timezone")) {

                    if (args.length < 2) {
                        event.getChannel().sendMessage(EmoteReference.ERROR + "You need to specify the timezone.").queue();
                        return;
                    }

                    try {
                        UtilsCmds.dateGMT(event.getGuild(), args[1]);
                    }
                    catch (Exception e) {
                        event.getChannel().sendMessage(EmoteReference.ERROR + "Not a valid timezone.").queue();
                        return;
                    }

                    u1.getData().setTimezone(args[1]);
                    u1.saveAsync();
                    event.getChannel().sendMessage(EmoteReference.CORRECT + "Saved timezone, your profile timezone is now: **" + args[1]
                            + "**").queue();
                    return;
                }

                if (args.length > 0 && args[0].equals("description")) {
                    if (args.length == 1) {
                        event.getChannel().sendMessage(EmoteReference.ERROR +
                                "You need to provide an argument! (set or remove)\n" +
                                "for example, ~>profile description set Hi there!").queue();
                        return;
                    }

                    if (args[1].equals("set")) {
                        int MAX_LENGTH = 300;
                        if (MantaroData.db().getUser(author).isPremium()) MAX_LENGTH = 500;
                        String content1 = SPLIT_PATTERN.split(content, 3)[2];

                        if (content1.length() > MAX_LENGTH) {
                            event.getChannel().sendMessage(EmoteReference.ERROR +
                                    "The description is too long! `(Limit of 300 characters for everyone and 500 for premium users)`")
                                    .queue();
                            return;
                        }

                        player.getData().setDescription(content1);
                        event.getChannel().sendMessage(EmoteReference.POPPER + "Set description to: **" + content1 + "**\n" +
                                "Check your shiny new profile with `~>profile`").queue();
                        player.saveAsync();
                        return;
                    }

                    if (args[1].equals("clear")) {
                        player.getData().setDescription(null);
                        event.getChannel().sendMessage(EmoteReference.CORRECT + "Successfully cleared description.").queue();
                        player.saveAsync();
                        return;
                    }
                }

                UserData user = MantaroData.db().getUser(event.getMember()).getData();
                Member member = event.getMember();

                if (!event.getMessage().getMentionedUsers().isEmpty()) {
                    author = event.getMessage().getMentionedUsers().get(0);
                    member = event.getGuild().getMember(author);

                    if (author.isBot()) {
                        event.getChannel().sendMessage(EmoteReference.ERROR + "Bots have no profiles.").queue();
                        return;
                    }

                    user = MantaroData.db().getUser(author).getData();
                    player = MantaroData.db().getPlayer(member);
                }

                User user1 = getUserById(player.getData().getMarriedWith());
                String marriedSince = player.getData().marryDate();
                String anniversary = player.getData().anniversary();

                if (args.length > 0 && args[0].equals("anniversary")) {
                    if (anniversary == null) {
                        event.getChannel().sendMessage(EmoteReference.ERROR + "I don't see any anniversary here :(. Maybe you were " +
                                "married before this change was implemented, in that case do ~>marry anniversarystart").queue();
                        return;
                    }
                    event.getChannel().sendMessage(String.format("%sYour anniversary with **%s** is on %s. You married on **%s**",
                            EmoteReference.POPPER, user1.getName(), anniversary, marriedSince)).queue();
                    return;
                }

                event.getChannel().sendMessage(baseEmbed(event, (user1 == null || !player.getInventory().containsItem(Items.RING) ? "" :
                        EmoteReference.RING) + member.getEffectiveName() + "'s Profile", author.getEffectiveAvatarUrl())
                        .setThumbnail(author.getEffectiveAvatarUrl())
                        .setDescription(player.getData().getDescription() == null ? "No description set" : player.getData()
                                .getDescription())
                        .addField(EmoteReference.DOLLAR + "Credits", "$ " + player.getMoney(), false)
                        .addField(EmoteReference.ZAP + "Level", player.getLevel() + " (Experience: " + player.getData().getExperience() +
                                ")", true)
                        .addField(EmoteReference.REP + "Reputation", String.valueOf(player.getReputation()), true)
                        .addField(EmoteReference.POUCH + "Inventory", ItemStack.toString(player.getInventory().asList()), false)
                        .addField(EmoteReference.POPPER + "Birthday", user.getBirthday() != null ? user.getBirthday().substring(0, 5) :
                                "Not specified.", true)
                        .addField(EmoteReference.HEART + "Married with", user1 == null ? "Nobody." : user1.getName() + "#" +
                                user1.getDiscriminator(), true)
                        .setFooter("User's timezone: " + (user.getTimezone() == null ? "No timezone set." : user.getTimezone() + " | " +
                                "Requested by " + event.getAuthor().getName()), event.getAuthor().getAvatarUrl())
                        .build()
                ).queue();
            }

            @Override
            public MessageEmbed help(GuildMessageReceivedEvent event) {
                return helpEmbed(event, "Profile command.")
                        .setDescription("**Retrieves your current user profile.**")
                        .addField("Usage", "To retrieve your profile, `~>profile`\n" +
                                "To change your description do `~>profile description set <description>`\n" +
                                "To clear it, just do `~>profile description clear`\n" +
                                "To set your timezone do `~>profile timezone <timezone>`", false)
                        .build();
            }
        });
    }

    @Command
    public static void rep(CommandRegistry cr) {
        cr.register("rep", new SimpleCommand(Category.CURRENCY) {
            RateLimiter rateLimiter = new RateLimiter(TimeUnit.HOURS, 12);

            @Override
            public void call(GuildMessageReceivedEvent event, String content, String[] args) {
                if (event.getMessage().getMentionedUsers().isEmpty()) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "You need to mention at least one user.").queue();
                    return;
                }

                if (event.getMessage().getMentionedUsers().get(0).isBot()) {
                    event.getChannel().sendMessage(EmoteReference.THINKING + "You cannot rep a bot.").queue();
                    return;
                }

                if (event.getMessage().getMentionedUsers().get(0).equals(event.getAuthor())) {
                    event.getChannel().sendMessage(EmoteReference.THINKING + "You cannot rep yourself.").queue();
                    return;
                }

                if (event.getMessage().getMentionedUsers().isEmpty()) {
                    event.getChannel().sendMessage(EmoteReference.THINKING + "You need to mention one user.").queue();
                    return;
                }

                if (!rateLimiter.process(event.getMember())) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "You can only rep once every 12 hours.").queue();
                    return;
                }
                User mentioned = event.getMessage().getMentionedUsers().get(0);
                Player player = MantaroData.db().getPlayer(event.getGuild().getMember(mentioned));
                player.addReputation(1L);
                player.saveAsync();
                event.getChannel().sendMessage(EmoteReference.CORRECT + "Added reputation to **" + mentioned.getName() + "**").queue();
            }

            @Override
            public MessageEmbed help(GuildMessageReceivedEvent event) {
                return helpEmbed(event, "Reputation command")
                        .setDescription("**Reps an user**")
                        .addField("Usage", "`~>rep <@user>` - **Gives reputation to x user**", false)
                        .addField("Parameters", "`@user` - user to mention", false)
                        .addField("Important", "Only usable every 24 hours.", false)
                        .build();
            }
        });
    }

    @Command
    public static void richest(CommandRegistry cr) {
        cr.register("richest", new SimpleCommand(Category.CURRENCY) {
            RateLimiter rateLimiter = new RateLimiter(TimeUnit.SECONDS, 10);

            @Override
            public void call(GuildMessageReceivedEvent event, String content, String[] args) {

                if (!rateLimiter.process(event.getMember())) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "Dang! Don't you think you're going a bit too fast?").queue();
                    return;
                }

                String pattern = ":g$";

                AtomicInteger i = new AtomicInteger();

                Cursor<Map> c1 = r.table("players")
                        .orderBy()
                        .optArg("index", r.desc("money"))
                        .filter(player -> player.g("id").match(pattern))
                        .map(player -> player.pluck("id", "money"))
                        .limit(15)
                        .run(MantaroData.conn(), OptArgs.of("read_mode", "outdated"));
                List<Map> c = c1.toList();

                event.getChannel().sendMessage(
                        baseEmbed(event,
                                "Global richest Users",
                                event.getJDA().getSelfUser().getEffectiveAvatarUrl()
                        ).setDescription(c.stream()
                                .map(map -> Pair.of(getUserById(map.get("id").toString().split(":")[0]), map.get("money").toString()))
                                .filter(p -> Objects.nonNull(p.getKey()))
                                .map(p -> String.format("%d - **%s#%s** - Credits: $%s", i.incrementAndGet(), p.getKey().getName(), p
                                        .getKey().getDiscriminator(), p.getValue()))
                                .collect(Collectors.joining("\n"))
                        ).build()
                ).queue();
            }

            @Override
            public MessageEmbed help(GuildMessageReceivedEvent event) {
                return helpEmbed(event, "Money list")
                        .setDescription("**Returns the global richest users.**")
                        .addField("Usage", "`~>richest` - **Returns the magical list.** ", false)
                        .build();
            }
        });
    }

    @Command
    public static void transferItems(CommandRegistry cr) {
        cr.register("itemtransfer", new SimpleCommand(Category.CURRENCY) {
            @Override
            protected void call(GuildMessageReceivedEvent event, String content, String[] args) {
                if (args.length < 2) {
                    onError(event);
                    return;
                }

                List<User> mentionedUsers = event.getMessage().getMentionedUsers();
                if (mentionedUsers.size() == 0) event.getChannel().sendMessage(EmoteReference.ERROR + "You need to mention a user").queue();
                else {
                    User giveTo = mentionedUsers.get(0);

                    if(event.getAuthor().getId().equals(giveTo.getId())){
                        event.getChannel().sendMessage(EmoteReference.ERROR + "You cannot transfer an item to yourself!").queue();
                        return;
                    }

                    Item item = Items.fromAny(args[1]).orElse(null);
                    if (item == null) {
                        event.getChannel().sendMessage("There isn't an item associated with this emoji.").queue();
                    }
                    else {
                        Player player = MantaroData.db().getPlayer(event.getAuthor());
                        Player giveToPlayer = MantaroData.db().getPlayer(giveTo);
                        if (args.length == 2) {
                            if (player.getInventory().containsItem(item)) {
                                if (item.isHidden()) {
                                    event.getChannel().sendMessage(EmoteReference.ERROR + "You cannot transfer this item!").queue();
                                    return;
                                }

                                player.getInventory().process(new ItemStack(item, -1));
                                giveToPlayer.getInventory().process(new ItemStack(item, 1));
                                event.getChannel().sendMessage(EmoteReference.OK + event.getAuthor().getAsMention() + " gave 1 " + item
                                        .getName() + " to " + giveTo.getAsMention()).queue();
                            }
                            else {
                                event.getChannel().sendMessage(EmoteReference.ERROR + "You don't have any of these items in your inventory")
                                        .queue();
                            }
                            player.saveAsync();
                            giveToPlayer.saveAsync();
                            return;
                        }

                        try {
                            int amount = Math.abs(Integer.parseInt(args[2]));
                            if (player.getInventory().containsItem(item) && player.getInventory().getAmount(item) >= amount) {
                                if (item.isHidden()) {
                                    event.getChannel().sendMessage(EmoteReference.ERROR + "You cannot transfer this item!").queue();
                                    return;
                                }

                                player.getInventory().process(new ItemStack(item, amount * -1));
                                giveToPlayer.getInventory().process(new ItemStack(item, amount));
                                event.getChannel().sendMessage(EmoteReference.OK + event.getAuthor().getAsMention() + " gave " + amount +
                                        " " + item.getName() + " to " + giveTo.getAsMention()).queue();
                            }
                            else event.getChannel().sendMessage(EmoteReference.ERROR + "You don't have enough of this item " +
                                    "to do that").queue();
                        }
                        catch (NumberFormatException nfe) {
                            event.getChannel().sendMessage(EmoteReference.ERROR + "Invalid number provided").queue();
                        }
                        player.saveAsync();
                        giveToPlayer.saveAsync();
                    }
                }
            }

            @Override
            public MessageEmbed help(GuildMessageReceivedEvent event) {
                return helpEmbed(event, "Transfer Items command")
                        .setDescription("**Transfers items from you to another player.**")
                        .addField("Usage", "`~>itemtransfer <@user> <item emoji> <amount (optional)>` - **Transfers the item to player x**",
                                false)
                        .addField("Parameters", "`@user` - user to send the item to\n" +
                                "`item emoji` - write out the emoji of the item you want to send\n" +
                                "`amount` - optional, send a specific amount of an item to someone.", false)
                        .addField("Important", "You cannot send more items than what you already have", false)
                        .build();
            }
        });

        cr.registerAlias("itemtransfer", "transferitems");
    }

    @Command
    public static void transfer(CommandRegistry cr) {
        cr.register("transfer", new SimpleCommand(Category.CURRENCY) {
            @Override
            public void call(GuildMessageReceivedEvent event, String content, String[] args) {
                if (event.getMessage().getMentionedUsers().isEmpty()) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "You need to mention one user.").queue();
                    return;
                }

                if (event.getMessage().getMentionedUsers().get(0).equals(event.getAuthor())) {
                    event.getChannel().sendMessage(EmoteReference.THINKING + "You cannot transfer money to yourself.").queue();
                    return;
                }

                long toSend;
                try {
                    toSend = Math.abs(Long.parseLong(args[1]));
                }
                catch (Exception e) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "You need to specify the amount.").queue();
                    return;
                }

                if (toSend == 0) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "You cannot transfer no money :P").queue();
                     return;
                }

                Player transferPlayer = MantaroData.db().getPlayer(event.getMember());
                if (transferPlayer.getMoney() < toSend) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "You cannot transfer money you don't have.").queue();
                    return;
                }

                User user = event.getMessage().getMentionedUsers().get(0);
                if (user.isBot() && !user.getId().equals("224662505157427200")) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "You cannot transfer money to a bot.").queue();
                    return;
                }

                Player toTransfer = MantaroData.db().getPlayer(event.getGuild().getMember(user));
                if (toTransfer.getMoney() + toSend < 0) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "Don't do that.").queue();
                    return;
                }
                if (toTransfer.addMoney(toSend)) {
                    transferPlayer.removeMoney(toSend);
                    transferPlayer.save();
                    toTransfer.save();

                    if (user.getId().equals("224662505157427200")) {
                        user.openPrivateChannel().queue(channel ->
                                channel.sendMessage(event.getAuthor().getId() + " transferred **" + toSend + "** to you successfully."
                                ).queue());
                    }

                    event.getChannel().sendMessage(EmoteReference.CORRECT + "Transferred **" + toSend + "** to *" + event.getMessage()
                            .getMentionedUsers().get(0).getName() + "* successfully.").queue();
                }
                else {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "Don't do that.").queue();
                }
            }

            @Override
            public MessageEmbed help(GuildMessageReceivedEvent event) {
                return helpEmbed(event, "Transfer command")
                        .setDescription("**Transfers money from you to another player.**")
                        .addField("Usage", "`~>transfer <@user> <money>` - **Tranfers money to player x**", false)
                        .addField("Parameters", "`@user` - user to send money to\n" +
                                "`money` - money to transfer.", false)
                        .addField("Important", "You cannot send more money than what you already have", false)
                        .build();
            }
        });
    }


    @Command
    public static void lootcrate(CommandRegistry registry) {
        registry.register("opencrate", new SimpleCommand(Category.CURRENCY) {

            RateLimiter rateLimiter = new RateLimiter(TimeUnit.HOURS, 1);

            @Override
            protected void call(GuildMessageReceivedEvent event, String content, String[] args) {
                String id = event.getAuthor().getId();

                if (!rateLimiter.process(id)) {
                    event.getChannel().sendMessage(EmoteReference.STOPWATCH +
                            "Cooldown a lil bit, you can only do this once every 1 hour.\n**You'll be able to use this command again " +
                            "in " +
                            Utils.getVerboseTime(Math.abs(System.currentTimeMillis() - rateLimiter.getUsersRateLimited().get(id)))
                            + ".**").queue();
                    return;
                }


                Player player = MantaroData.db().getPlayer(event.getAuthor());
                Inventory inventory = player.getInventory();
                if (inventory.containsItem(Items.LOOT_CRATE)) {
                    if (inventory.containsItem(Items.LOOT_CRATE_KEY)) {
                        inventory.process(new ItemStack(Items.LOOT_CRATE_KEY, -1));
                        inventory.process(new ItemStack(Items.LOOT_CRATE, -1));
                        player.save();
                        openLootBox(event, true);
                    }
                    else {
                        event.getChannel().sendMessage(EmoteReference.ERROR + "You need a loot crate key to open a crate. It's locked!")
                                .queue();
                    }
                }
                else {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "You need a loot crate! How else would you use your key >" +
                            ".>").queue();
                }
            }

            @Override
            public MessageEmbed help(GuildMessageReceivedEvent event) {
                return helpEmbed(event, "Open loot crates")
                        .setDescription("**Yep. It's really that simple**")
                        .build();
            }
        });
    }


    public static void openLootBox(GuildMessageReceivedEvent event, boolean special) {
        List<Item> toAdd = new ArrayList<>();
        int amtItems = random.nextInt(3) + 3;
        List<Item> items = new ArrayList<>();
        items.addAll(Arrays.asList(Items.ALL));
        items.removeIf(item -> item.isHidden() || !item.isBuyable() || !item.isSellable());
        items.sort((o1, o2) -> {
            if (o1.getValue() > o2.getValue()) return 1;
            if (o1.getValue() == o2.getValue()) return 0;
            return -1;
        });
        if (!special) {
            for (Item i : Items.ALL) if (i.isHidden() || !i.isBuyable() || i.isSellable()) items.add(i);
        }
        for (int i = 0; i < amtItems; i++) toAdd.add(selectReverseWeighted(items));
        Player player = MantaroData.db().getPlayer(event.getMember());
        ArrayList<ItemStack> ita = new ArrayList<>();
        toAdd.forEach(item -> ita.add(new ItemStack(item, 1)));
        player.getInventory().process(ita);
        player.save();
        event.getChannel().sendMessage(EmoteReference.LOOT_CRATE.getDiscordNotation() + "**You won:** " +
                toAdd.stream().map(Item::toString).collect(Collectors.joining(", "))).queue();
    }

    private static Item selectReverseWeighted(List<Item> items) {
        Map<Integer, Item> weights = new HashMap<>();
        int weightedTotal = 0;
        for (int i = 0; i < items.size(); i++) {
            int t = items.size() - i;
            weightedTotal += t;
            weights.put(t, items.get(i));
        }
        final int[] selected = {random.nextInt(weightedTotal)};
        for (Map.Entry<Integer, Item> i : weights.entrySet()) {
            if ((selected[0] -= i.getKey()) <= 0) {
                return i.getValue();
            }
        }
        return null;
    }

    private static User getUserById(String id) {
        if (id == null) return null;
        MantaroShard shard1 = MantaroBot.getInstance().getShardList().stream().filter(shard ->
                shard.getJDA().getUserById(id) != null).findFirst().orElse(null);
        return shard1 == null ? null : shard1.getUserById(id);
    }

    private static void proceedGamble(GuildMessageReceivedEvent event, Player player, int luck, Random r, long i, long gains) {
        if (luck > r.nextInt(110)) {
            if (player.addMoney(gains)) {
                event.getChannel().sendMessage(EmoteReference.DICE + "Congrats, you won " + gains + " credits and got to keep what you " +
                        "had!").queue();
            }
            else {
                event.getChannel().sendMessage(EmoteReference.DICE + "Congrats, you won " + gains + " credits. But you already had too " +
                        "many credits. Your bag overflowed.\nCongratulations, you exploded a Java long. Here's a buggy money bag for you" +
                        ".").queue();
            }
        }
        else {
            player.setMoney(Math.max(0, player.getMoney() - i));
            event.getChannel().sendMessage("\uD83C\uDFB2 Sadly, you lost " + (player.getMoney() == 0 ? "all your" : i) + " credits! " +
                    "\uD83D\uDE26").queue();
        }

        player.saveAsync();
    }
}
