package org.ibex.classgen;

public interface CGConst {

    // Class only
    public static final int INTERFACE = 0x0200;
    public static final int SUPER     = 0x0020;

    // Field/Method only
    public static final int PUBLIC    = 0x0001;
    public static final int PRIVATE   = 0x0002;
    public static final int PROTECTED = 0x0004;
    public static final int STATIC    = 0x0008;
    public static final int FINAL     = 0x0010;

    // Class/Method only
    public static final int ABSTRACT  = 0x0400;
    
    // Method Only
    public static final int SYNCHRONIZED = 0x0020;
    public static final int NATIVE       = 0x0100;
    public static final int STRICT       = 0x0800;
    public static final int VALID_METHOD_FLAGS = PUBLIC|PRIVATE|PROTECTED|STATIC|FINAL|SYNCHRONIZED|NATIVE|ABSTRACT|STRICT;

    // Field only
    public static final int VOLATILE  = 0x0040;
    public static final int TRANSIENT = 0x0080;
    public static final int VALID_FIELD_FLAGS = PUBLIC|PRIVATE|PROTECTED|VOLATILE|TRANSIENT|STATIC|FINAL;

    
    // Constant Pool Stuff
    public static final int CONSTANT_UTF8 = 1;
    public static final int CONSTANT_INTEGER = 3;
    public static final int CONSTANT_FLOAT = 4;
    public static final int CONSTANT_LONG = 5;
    public static final int CONSTANT_DOUBLE = 6;
    public static final int CONSTANT_CLASS = 7;
    public static final int CONSTANT_STRING = 8;
    public static final int CONSTANT_FIELDREF = 9;
    public static final int CONSTANT_METHODREF = 10;
    public static final int CONSTANT_INTERFACEMETHODREF = 11;
    public static final int CONSTANT_NAMEANDTYPE = 12;
    
    // Instructions
    
    // NOTE: DO NOT modify or remove the number in the comments
    
    public static final byte NOP = (byte) 0x0; // 0
    public static final byte ACONST_NULL = (byte) 0x01; // 0
    public static final byte ICONST_M1 = (byte) 0x02; // 0
    public static final byte ICONST_0 = (byte) 0x03; // 0
    public static final byte ICONST_1 = (byte) 0x04; // 0
    public static final byte ICONST_2 = (byte) 0x05; // 0
    public static final byte ICONST_3 = (byte) 0x06; // 0
    public static final byte ICONST_4 = (byte) 0x07; // 0
    public static final byte ICONST_5 = (byte) 0x08; // 0
    public static final byte LCONST_0 = (byte) 0x09; // 0
    public static final byte LCONST_1 = (byte) 0x0A; // 0
    public static final byte FCONST_0 = (byte) 0x0B; // 0
    public static final byte FCONST_1 = (byte) 0x0C; // 0
    public static final byte FCONST_2 = (byte) 0x0D; // 0
    public static final byte DCONST_0 = (byte) 0x0E; // 0
    public static final byte DCONST_1 = (byte) 0x0F; // 0
    public static final byte BIPUSH = (byte) 0x10; // 1
    public static final byte SIPUSH = (byte) 0x11; // 2
    public static final byte LDC = (byte) 0x12; // 1C
    public static final byte LDC_W = (byte) 0x13; // 2C
    public static final byte LDC2_W = (byte) 0x14; // 2C
    public static final byte ILOAD = (byte) 0x15; // 1U
    public static final byte LLOAD = (byte) 0x16; // 1U
    public static final byte FLOAD = (byte) 0x17; // 1U
    public static final byte DLOAD = (byte) 0x18; // 1U
    public static final byte ALOAD = (byte) 0x19; // 1U
    public static final byte ILOAD_0 = (byte) 0x1A; // 0
    public static final byte ILOAD_1 = (byte) 0x1B; // 0
    public static final byte ILOAD_2 = (byte) 0x1C; // 0
    public static final byte ILOAD_3 = (byte) 0x1D; // 0
    public static final byte LLOAD_0 = (byte) 0x1E; // 0
    public static final byte LLOAD_1 = (byte) 0x1F; // 0
    public static final byte LLOAD_2 = (byte) 0x20; // 0
    public static final byte LLOAD_3 = (byte) 0x21; // 0
    public static final byte FLOAD_0 = (byte) 0x22; // 0
    public static final byte FLOAD_1 = (byte) 0x23; // 0
    public static final byte FLOAD_2 = (byte) 0x24; // 0
    public static final byte FLOAD_3 = (byte) 0x25; // 0
    public static final byte DLOAD_0 = (byte) 0x26; // 0
    public static final byte DLOAD_1 = (byte) 0x27; // 0
    public static final byte DLOAD_2 = (byte) 0x28; // 0
    public static final byte DLOAD_3 = (byte) 0x29; // 0
    public static final byte ALOAD_0 = (byte) 0x2A; // 0
    public static final byte ALOAD_1 = (byte) 0x2B; // 0
    public static final byte ALOAD_2 = (byte) 0x2C; // 0
    public static final byte ALOAD_3 = (byte) 0x2D; // 0
    public static final byte IALOAD = (byte) 0x2E; // 0
    public static final byte LALOAD = (byte) 0x2F; // 0
    public static final byte FALOAD = (byte) 0x30; // 0
    public static final byte DALOAD = (byte) 0x31; // 0
    public static final byte AALOAD = (byte) 0x32; // 0
    public static final byte BALOAD = (byte) 0x33; // 0
    public static final byte CALOAD = (byte) 0x34; // 0
    public static final byte SALOAD = (byte) 0x35; // 0
    public static final byte ISTORE = (byte) 0x36; // 1U
    public static final byte LSTORE = (byte) 0x37; // 1U
    public static final byte FSTORE = (byte) 0x38; // 1U
    public static final byte DSTORE = (byte) 0x39; // 1U
    public static final byte ASTORE = (byte) 0x3A; // 1U
    public static final byte ISTORE_0 = (byte) 0x3B; // 0
    public static final byte ISTORE_1 = (byte) 0x3C; // 0
    public static final byte ISTORE_2 = (byte) 0x3D; // 0
    public static final byte ISTORE_3 = (byte) 0x3E; // 0
    public static final byte LSTORE_0 = (byte) 0x3F; // 0
    public static final byte LSTORE_1 = (byte) 0x40; // 0
    public static final byte LSTORE_2 = (byte) 0x41; // 0
    public static final byte LSTORE_3 = (byte) 0x42; // 0
    public static final byte FSTORE_0 = (byte) 0x43; // 0
    public static final byte FSTORE_1 = (byte) 0x44; // 0
    public static final byte FSTORE_2 = (byte) 0x45; // 0
    public static final byte FSTORE_3 = (byte) 0x46; // 0
    public static final byte DSTORE_0 = (byte) 0x47; // 0
    public static final byte DSTORE_1 = (byte) 0x48; // 0
    public static final byte DSTORE_2 = (byte) 0x49; // 0
    public static final byte DSTORE_3 = (byte) 0x4A; // 0
    public static final byte ASTORE_0 = (byte) 0x4B; // 0
    public static final byte ASTORE_1 = (byte) 0x4C; // 0
    public static final byte ASTORE_2 = (byte) 0x4D; // 0
    public static final byte ASTORE_3 = (byte) 0x4E; // 0
    public static final byte IASTORE = (byte) 0x4F; // 0
    public static final byte LASTORE = (byte) 0x50; // 0
    public static final byte FASTORE = (byte) 0x51; // 0
    public static final byte DASTORE = (byte) 0x52; // 0
    public static final byte AASTORE = (byte) 0x53; // 0
    public static final byte BASTORE = (byte) 0x54; // 0
    public static final byte CASTORE = (byte) 0x55; // 0
    public static final byte SASTORE = (byte) 0x56; // 0
    public static final byte POP = (byte) 0x57; // 0
    public static final byte POP2 = (byte) 0x58; // 0
    public static final byte DUP = (byte) 0x59; // 0
    public static final byte DUP_X1 = (byte) 0x5A; // 0
    public static final byte DUP_X2 = (byte) 0x5B; // 0
    public static final byte DUP2 = (byte) 0x5C; // 0
    public static final byte DUP2_X1 = (byte) 0x5D; // 0
    public static final byte DUP2_X2 = (byte) 0x5E; // 0
    public static final byte SWAP = (byte) 0x5F; // 0
    public static final byte IADD = (byte) 0x60; // 0
    public static final byte LADD = (byte) 0x61; // 0
    public static final byte FADD = (byte) 0x62; // 0
    public static final byte DADD = (byte) 0x63; // 0
    public static final byte ISUB = (byte) 0x64; // 0
    public static final byte LSUB = (byte) 0x65; // 0
    public static final byte FSUB = (byte) 0x66; // 0
    public static final byte DSUB = (byte) 0x67; // 0
    public static final byte IMUL = (byte) 0x68; // 0
    public static final byte LMUL = (byte) 0x69; // 0
    public static final byte FMUL = (byte) 0x6A; // 0
    public static final byte DMUL = (byte) 0x6B; // 0
    public static final byte IDIV = (byte) 0x6C; // 0
    public static final byte LDIV = (byte) 0x6D; // 0
    public static final byte FDIV = (byte) 0x6E; // 0
    public static final byte DDIV = (byte) 0x6F; // 0
    public static final byte IREM = (byte) 0x70; // 0
    public static final byte LREM = (byte) 0x71; // 0
    public static final byte FREM = (byte) 0x72; // 0
    public static final byte DREM = (byte) 0x73; // 0
    public static final byte INEG = (byte) 0x74; // 0
    public static final byte LNEG = (byte) 0x75; // 0
    public static final byte FNEG = (byte) 0x76; // 0
    public static final byte DNEG = (byte) 0x77; // 0
    public static final byte ISHL = (byte) 0x78; // 0
    public static final byte LSHL = (byte) 0x79; // 0
    public static final byte ISHR = (byte) 0x7A; // 0
    public static final byte LSHR = (byte) 0x7B; // 0
    public static final byte IUSHR = (byte) 0x7C; // 0
    public static final byte LUSHR = (byte) 0x7D; // 0
    public static final byte IAND = (byte) 0x7E; // 0
    public static final byte LAND = (byte) 0x7F; // 0
    public static final byte IOR = (byte) 0x80; // 0
    public static final byte LOR = (byte) 0x81; // 0
    public static final byte IXOR = (byte) 0x82; // 0
    public static final byte LXOR = (byte) 0x83; // 0
    public static final byte IINC = (byte) 0x84; // 2
    public static final byte I2L = (byte) 0x85; // 0
    public static final byte I2F = (byte) 0x86; // 0
    public static final byte I2D = (byte) 0x87; // 0
    public static final byte L2I = (byte) 0x88; // 0
    public static final byte L2F = (byte) 0x89; // 0
    public static final byte L2D = (byte) 0x8A; // 0
    public static final byte F2I = (byte) 0x8B; // 0
    public static final byte F2L = (byte) 0x8C; // 0
    public static final byte F2D = (byte) 0x8D; // 0
    public static final byte D2I = (byte) 0x8E; // 0
    public static final byte D2L = (byte) 0x8F; // 0
    public static final byte D2F = (byte) 0x90; // 0
    public static final byte I2B = (byte) 0x91; // 0
    public static final byte I2C = (byte) 0x92; // 0
    public static final byte I2S = (byte) 0x93; // 0
    public static final byte LCMP = (byte) 0x94; // 0
    public static final byte FCMPL = (byte) 0x95; // 0
    public static final byte FCMPG = (byte) 0x96; // 0
    public static final byte DCMPL = (byte) 0x97; // 0
    public static final byte DCMPG = (byte) 0x98; // 0
    public static final byte IFEQ = (byte) 0x99; // 2B
    public static final byte IFNE = (byte) 0x9A; // 2B
    public static final byte IFLT = (byte) 0x9B; // 2B
    public static final byte IFGE = (byte) 0x9C; // 2B
    public static final byte IFGT = (byte) 0x9D; // 2B
    public static final byte IFLE = (byte) 0x9E; // 2B
    public static final byte IF_ICMPEQ = (byte) 0x9F; // 2B
    public static final byte IF_ICMPNE = (byte) 0xA0; // 2B
    public static final byte IF_ICMPLT = (byte) 0xA1; // 2B
    public static final byte IF_ICMPGE = (byte) 0xA2; // 2B
    public static final byte IF_ICMPGT = (byte) 0xA3; // 2B
    public static final byte IF_ICMPLE = (byte) 0xA4; // 2B
    public static final byte IF_ACMPEQ = (byte) 0xA5; // 2B
    public static final byte IF_ACMPNE = (byte) 0xA6; // 2B
    public static final byte GOTO = (byte) 0xA7; // 2B
    public static final byte JSR = (byte) 0xA8; // 2B
    public static final byte RET = (byte) 0xA9; // 1
    public static final byte TABLESWITCH = (byte) 0xAA; // V
    public static final byte LOOKUPSWITCH = (byte) 0xAB;  // V
    public static final byte IRETURN = (byte) 0xAC; // 0
    public static final byte LRETURN = (byte) 0xAD; // 0
    public static final byte FRETURN = (byte) 0xAE; // 0
    public static final byte DRETURN = (byte) 0xAF; // 0
    public static final byte ARETURN = (byte) 0xB0; // 0
    public static final byte RETURN = (byte) 0xB1; // 0
    public static final byte GETSTATIC = (byte) 0xB2; // 2C
    public static final byte PUTSTATIC = (byte) 0xB3; // 2C
    public static final byte GETFIELD = (byte) 0xB4; // 2C
    public static final byte PUTFIELD = (byte) 0xB5; // 2C
    public static final byte INVOKEVIRTUAL = (byte) 0xB6; // 2C
    public static final byte INVOKESPECIAL = (byte) 0xB7; // 2C
    public static final byte INVOKESTATIC = (byte) 0xB8; // 2C
    public static final byte INVOKEINTERFACE = (byte) 0xB9; // 4C
    public static final byte NEW = (byte) 0xBB; // 2C
    public static final byte NEWARRAY = (byte) 0xBC; // 1
    public static final byte ANEWARRAY = (byte) 0xBD; // 2C
    public static final byte ARRAYLENGTH = (byte) 0xBE; // 0
    public static final byte ATHROW = (byte) 0xBF; // 0
    public static final byte CHECKCAST = (byte) 0xC0; // 2C
    public static final byte INSTANCEOF = (byte) 0xC1; // 2C
    public static final byte MONITORENTER = (byte) 0xC2; // 0
    public static final byte MONITOREXIT = (byte) 0xC3; // 0
    public static final byte WIDE = (byte) 0xC4; // V
    public static final byte MULTIANEWARRAY = (byte) 0xC5; // 3C
    public static final byte IFNULL = (byte) 0xC6; // 2B
    public static final byte IFNONNULL = (byte) 0xC7; // 2B
    public static final byte GOTO_W = (byte) 0xC8; // 4B
    public static final byte JSR_W = (byte) 0xC9; // 4B
}

// DO NOT REMOVE THIS

/*
#!/usr/bin/perl -w
@ARGV || push @ARGV, $0;
my @a = ();
my @names = ();
while(<>) {
        chomp;
        next unless(m|byte ([A-Z0-9_]+) = .*?([0-9xA-F]+);\s+//\s*(.*)$|i);
        my ($name, $num) = ($1, hex($2));
        $_ = $3;
        my $n = 1<<6;
        $n |= s/^(\d+)// ? $1 : (s/^V//||die, 7);
        $n |= (1<<4) if(s/^C//);
        $n |= (1<<3) if(s/^B//);
        $n |= (1<<5) if(s/^U//);
        die if(/./);
        $a[$num] = $n;
        $names[$num] = $name;
}
print "private static final byte[] OP_DATA = {\n\t";
for(my $i=0;$i<256;$i++) {
        printf "0x%02x%s", $a[$i]||1, $i==255?"\n};\n":($i%16)==15?", \n\t":", ";
}
print "final String[] OP_NAMES = new String[]{\n\t";
for(my $i=0;$i<256;$i++) {
    printf "\"%s\"%s", lc($names[$i]||""), $i==255?"\n};\n":($i%6)==5?", \n\t":", ";
}
__END__
*/
