package org.pitest.mutationtest.engine.gregor.mutators.experimental;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;

public enum BigIntegerMutator implements MethodMutatorFactory {
  INSTANCE;

  @Override
  public MethodVisitor create(MutationContext context, MethodInfo info, MethodVisitor visitor) {
    return new BigIntegerMathMutator(this, context, visitor);
  }

  @Override
  public String getGloballyUniqueId() {
    return this.getClass().getName();
  }

  @Override
  public String toString() {
    return "EXPERIMENTAL_BIGINTEGER_MATH_MUTATOR";
  }

  @Override
  public String getName() {
    return toString();
  }

  private static final class BigIntegerMathMutator extends MethodVisitor {

    private static final List<Replacement> REPLACEMENTS;

    static {
      String unary = "(Ljava/math/BigInteger;)Ljava/math/BigInteger;";
      List<Replacement> list = new ArrayList<>();
      list.add(new Replacement("add", "subtract", unary));
      list.add(new Replacement("subtract", "add", unary));
      list.add(new Replacement("multiply", "divide", unary));
      list.add(new Replacement("divide", "multiply", unary));
      list.add(new Replacement("mod", "divide", unary));

      list.add(new Replacement("shiftLeft", "shiftRight", unary));
      list.add(new Replacement("shiftRight", "shiftLeft", unary));
      list.add(new Replacement("and", "or", unary));
      list.add(new Replacement("or", "and", unary));
      list.add(new Replacement("xor", "and", unary));
      list.add(new Replacement("andNot", "and", unary));

      list.add(new Replacement("max", "min", unary));
      list.add(new Replacement("min", "max", unary));

      String intAsParam = "(I)Ljava/math/BigInteger;";
      list.add(new Replacement("setBit", "flipBit", intAsParam));
      list.add(new Replacement("clearBit", "flipBit", intAsParam));
      list.add(new Replacement("flipBit", "setBit", intAsParam));

      String noParams = "()Ljava/math/BigInteger;";
      list.add(new Replacement("abs", "negate", noParams));
      list.add(new Replacement("not", "negate", noParams));
      list.add(new Replacement("negate", "not", noParams));

      REPLACEMENTS = Collections.unmodifiableList(list);
    }

    private final MethodMutatorFactory factory;
    private final MutationContext context;
    private final String expectedOwner = "java/math/BigInteger";

    private BigIntegerMathMutator(MethodMutatorFactory factory, MutationContext context,
        MethodVisitor visitor) {
      super(Opcodes.ASM6, visitor);

      this.factory = factory;
      this.context = context;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
        boolean isInterface) {
      if (!owner.equals(expectedOwner) || opcode != Opcodes.INVOKEVIRTUAL) {
        this.mv.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        return;
      }

      if (runReplacements(opcode, owner, name, descriptor)) {
        this.mv.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
      }
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
        Object... bootstrapMethodArguments) {
      bootstrapMethodHandle = mutateHandle(bootstrapMethodHandle);
      Object[] methodArgs = new Object[bootstrapMethodArguments.length];
      for (int i = 0; i < bootstrapMethodArguments.length; i++) {
        Object bootstrapMethodArgument = bootstrapMethodArguments[i];
        if (bootstrapMethodArgument instanceof Handle) {
          methodArgs[i] = mutateHandle((Handle) bootstrapMethodArgument);
        } else {
          methodArgs[i] = bootstrapMethodArgument;
        }
      }
      super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, methodArgs);
    }

    private Handle mutateHandle(Handle handle) {
      int opcode = handle.getTag();
      String owner = handle.getOwner();
      String name = handle.getName();
      String descriptor = handle.getDesc();
      if (owner.equals(expectedOwner) && opcode == Opcodes.H_INVOKEVIRTUAL) {
        for (Replacement replacement : REPLACEMENTS) {
          if (replacement.descriptor.equals(descriptor) && replacement.sourceName.equals(name)) {
            MutationIdentifier identifier =
                context.registerMutation(factory, replacement.toString());
            if (context.shouldMutate(identifier)) {
              return new Handle(
                  opcode,
                  owner,
                  replacement.destinationName,
                  descriptor,
                  handle.isInterface());
            }
          }
        }
      }
      return handle;
    }

    private boolean runReplacements(int opcode, String owner, String name, String descriptor) {
      for (Replacement replacement : REPLACEMENTS) {
        if (replacement.descriptor.equals(descriptor) && replacement.sourceName.equals(name)) {
          MutationIdentifier identifier = context.registerMutation(factory, replacement.toString());
          if (context.shouldMutate(identifier)) {
            this.mv.visitMethodInsn(
                opcode,
                owner,
                replacement.destinationName,
                replacement.descriptor,
                false);
            return false;
          }
        }
      }
      return true;
    }

    private static final class Replacement {

      private final String sourceName;
      private final String destinationName;
      private final String descriptor;

      Replacement(String sourceName, String destinationName, String descriptor) {
        this.sourceName = sourceName;
        this.destinationName = destinationName;
        this.descriptor = descriptor;
      }

      @Override
      public String toString() {
        String template = "Replaced BigInteger#%s with BigInteger#%s.";
        return String.format(template, sourceName, destinationName);
      }
    }
  }
}
