package powercrystals.minefactoryreloaded.asm;

import cofh.asm.repack.codechicken.lib.asm.ASMHelper;
import cofh.asm.repack.codechicken.lib.asm.ModularASMTransformer;
import cofh.asm.repack.codechicken.lib.asm.ObfMapping;
import com.google.common.base.Throwables;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Iterator;

import static org.objectweb.asm.Opcodes.*;

public class MFRClassTransformer implements IClassTransformer {

	private ModularASMTransformer transformer = new ModularASMTransformer();

	public MFRClassTransformer() {

		loadTransformer();
	}

	@Override
	public byte[] transform(String name, String transformedName, byte[] bytes) {

		if (bytes == null) {
			return null;
		}

		bytes = transformer.transform(name, bytes);

		return bytes;
	}

	private void loadTransformer() {

		final String worldServer = "net/minecraft/world/WorldServer";
		final String world = "net/minecraft/world/World";
		final String serverSig;
		final String worldSig;
		{
			final String sigBody = "Lnet/minecraft/world/storage/ISaveHandler;Lnet/minecraft/world/storage/WorldInfo;" +
					"Lnet/minecraft/world/WorldProvider;Lnet/minecraft/profiler/Profiler;Z";
			serverSig = "(Lnet/minecraft/server/MinecraftServer;" + sigBody + ")V";
			worldSig = "(" + sigBody + ")V";
		}
		ObfMapping serverMapping = new ObfMapping(worldServer, "<init>", serverSig);

		transformer.add(new ModularASMTransformer.MethodTransformer(serverMapping) {

			@Override
			public void transform(ClassNode cnode) {

				{
					MethodNode mv = ASMHelper.findMethod(method, cnode);
					if (mv != null) {
						return;
					}
				}

				ObfMapping mcServer = new ObfMapping(worldServer, "field_73061_a", "Lnet/minecraft/server/MinecraftServer;")
						.toRuntime();
				ObfMapping[] nullFields = {
						new ObfMapping(worldServer, "field_73062_L", "Lnet/minecraft/entity/EntityTracker;").toRuntime(),
						new ObfMapping(worldServer, "field_73063_M",
								"Lnet/minecraft/server/management/PlayerChunkMap;").toRuntime(),
						new ObfMapping(worldServer, "field_85177_Q", "Lnet/minecraft/world/Teleporter;").toRuntime()
				};

				MethodVisitor mv = cnode.visitMethod(ACC_PUBLIC, "<init>", serverSig, null, null);
				mv.visitCode();
				mv.visitVarInsn(ALOAD, 0);
				mv.visitVarInsn(ALOAD, 2);
				mv.visitVarInsn(ALOAD, 3);
				mv.visitVarInsn(ALOAD, 4);
				mv.visitVarInsn(ALOAD, 5);
				mv.visitVarInsn(ILOAD, 6);
				// [World] super(saveHandler, worldInfo, provider, theProfiler, isRemote);
				mv.visitMethodInsn(INVOKESPECIAL, world, "<init>", worldSig, false);
				mv.visitVarInsn(ALOAD, 0);
				mv.visitVarInsn(ALOAD, 1);
				mcServer.visitFieldInsn(mv, PUTFIELD);
				for (ObfMapping field : nullFields) {
					mv.visitVarInsn(ALOAD, 0);
					mv.visitInsn(ACONST_NULL);
					field.visitFieldInsn(mv, PUTFIELD);
				}
				mv.visitInsn(RETURN);
				mv.visitMaxs(7, 7);
				mv.visitEnd();
			}

			public void transform(MethodNode mv) {

			}

		});
		transformer.add(new ModularASMTransformer.ClassNodeTransformer() {

			public String className() {

				return worldServer.replace('/', '.');
			}

			public void transform(ClassNode cn) {

				for (MethodNode a : cn.methods) {
					a.access = (a.access & ~(ACC_PRIVATE | ACC_PROTECTED | ACC_FINAL)) | ACC_PUBLIC;
				}
			}

		});
		transformer.add(new ModularASMTransformer.ClassNodeTransformer() {

			public String className() {

				return world.replace('/', '.');
			}

			public void transform(ClassNode cn) {

				for (MethodNode a : cn.methods) {
					a.access = (a.access & ~(ACC_PRIVATE | ACC_PROTECTED | ACC_FINAL)) | ACC_PUBLIC;
				}
			}

		});
		transformer.add(new ModularASMTransformer.ClassNodeTransformer() {

			public String className() {

				return "powercrystals.minefactoryreloaded.asm.hooks.WorldProxy";
			}

			public void transform(ClassNode cn) {

				Method[] worldMethods = null;
				try {
					worldMethods = net.minecraft.world.World.class.getDeclaredMethods();
				} catch (Throwable e) {
					Throwables.propagate(e);
				}

				for (Method m : worldMethods) {
					if (!Modifier.isStatic(m.getModifiers())) {
						String desc = Type.getMethodDescriptor(m);
						{
							Iterator<MethodNode> i = cn.methods.iterator();
							while (i.hasNext()) {
								MethodNode m2 = i.next();
								if (m2.name.equals(m.getName()) && m2.desc.equals(desc)) {
									i.remove();
								}
							}
						}
						MethodVisitor mv = cn.visitMethod(getAccess(m), m.getName(), desc, null, getExceptions(m));
						mv.visitCode();
						mv.visitVarInsn(ALOAD, 0);
						mv.visitFieldInsn(GETFIELD, cn.name, "proxiedWorld", "L" + world + ";");
						Type[] types = Type.getArgumentTypes(m);
						for (int i = 0, w = 1, e = types.length; i < e; i++) {
							mv.visitVarInsn(types[i].getOpcode(ILOAD), w);
							w += types[i].getSize();
						}
						mv.visitMethodInsn(INVOKEVIRTUAL, world, m.getName(), desc, false);
						mv.visitInsn(Type.getReturnType(m).getOpcode(IRETURN));
						mv.visitMaxs(types.length, 1);
						mv.visitEnd();
					}
				}
			}

		});
		transformer.add(new ModularASMTransformer.ClassNodeTransformer() {

			public String className() {

				return "powercrystals.minefactoryreloaded.asm.hooks.WorldServerProxy";
			}

			public void transform(ClassNode cn) {

				Method[] worldServerMethods = null;
				try {
					worldServerMethods = net.minecraft.world.WorldServer.class.getDeclaredMethods();
				} catch (Throwable e) {
					Throwables.propagate(e);
				}
				Method[] worldMethods = null;
				try {
					worldMethods = net.minecraft.world.World.class.getDeclaredMethods();
				} catch (Throwable e) {
					Throwables.propagate(e);
				}

				cn.superName = worldServer;
				for (MethodNode m : cn.methods) {
					if ("<init>".equals(m.name)) {
						InsnList l = m.instructions;
						for (int i = 0, e = l.size(); i < e; i++) {
							AbstractInsnNode n = l.get(i);
							if (n instanceof MethodInsnNode) {
								MethodInsnNode mn = (MethodInsnNode) n;
								if (mn.getOpcode() == INVOKESPECIAL) {
									mn.owner = cn.superName;
									break;
								}
							}
						}
					}
				}

				for (Method m : worldMethods) {
					if (!Modifier.isStatic(m.getModifiers())) {
						String desc = Type.getMethodDescriptor(m);
						{
							Iterator<MethodNode> i = cn.methods.iterator();
							while (i.hasNext()) {
								MethodNode m2 = i.next();
								if (m2.name.equals(m.getName()) && m2.desc.equals(desc)) {
									i.remove();
								}
							}
						}
						MethodVisitor mv = cn.visitMethod(getAccess(m), m.getName(), desc, null, getExceptions(m));
						mv.visitCode();
						mv.visitVarInsn(ALOAD, 0);
						mv.visitFieldInsn(GETFIELD, cn.name, "proxiedWorld", "L" + worldServer + ";");
						Type[] types = Type.getArgumentTypes(m);
						for (int i = 0, w = 1, e = types.length; i < e; i++) {
							mv.visitVarInsn(types[i].getOpcode(ILOAD), w);
							w += types[i].getSize();
						}
						mv.visitMethodInsn(INVOKEVIRTUAL, world, m.getName(), desc, false);
						mv.visitInsn(Type.getReturnType(m).getOpcode(IRETURN));
						mv.visitMaxs(types.length + 1, types.length + 1);
						mv.visitEnd();
					}
				}

				for (Method m : worldServerMethods) {
					if (!Modifier.isStatic(m.getModifiers())) {
						String desc = Type.getMethodDescriptor(m);
						{
							Iterator<MethodNode> i = cn.methods.iterator();
							while (i.hasNext()) {
								MethodNode m2 = i.next();
								if (m2.name.equals(m.getName()) && m2.desc.equals(desc)) {
									i.remove();
								}
							}
						}
						MethodVisitor mv = cn.visitMethod(getAccess(m), m.getName(), desc, null, getExceptions(m));
						mv.visitCode();
						mv.visitVarInsn(ALOAD, 0);
						mv.visitFieldInsn(GETFIELD, cn.name, "proxiedWorld", "L" + worldServer + ";");
						Type[] types = Type.getArgumentTypes(m);
						for (int i = 0, w = 1, e = types.length; i < e; i++) {
							mv.visitVarInsn(types[i].getOpcode(ILOAD), w);
							w += types[i].getSize();
						}
						mv.visitMethodInsn(INVOKEVIRTUAL, worldServer, m.getName(), desc, false);
						mv.visitInsn(Type.getReturnType(m).getOpcode(IRETURN));
						mv.visitMaxs(types.length + 1, types.length + 1);
						mv.visitEnd();
					}
				}
			}
		});

	}

	private static int getAccess(Method m) {

		int r = m.getModifiers();
		r &= ~(ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED | ACC_FINAL | ACC_BRIDGE | ACC_ABSTRACT);
		r |= ACC_PUBLIC | ACC_SYNTHETIC;
		return r;
	}

	private static String[] getExceptions(Method m) {

		Class<?>[] d = m.getExceptionTypes();
		if (d == null) {
			return null;
		}
		String[] r = new String[d.length];
		for (int i = 0; i < d.length; ++i) {
			r[i] = Type.getInternalName(d[i]);
		}
		return r;
	}
}
