package net.kodehawa.mantarobot.commands.options;

import net.kodehawa.mantarobot.utils.Utils;

public enum OptionType {
    GENERAL(0), SPECIFIC(1), COMMAND(2), GUILD(3), CHANNEL(4), USER(5), MUSIC(5);

    int level;

    OptionType(int level){
        this.level = level;
    }

    @Override
    public String toString(){
        return Utils.capitalize(this.name().toLowerCase());
    }
}