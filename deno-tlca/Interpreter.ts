import { inferExpression } from "./Infer.ts";
import {
  DataDeclaration,
  Expression,
  LetExpression,
  LetRecExpression,
  Op,
  parse,
  Pattern,
  Program,
  Type as TypeItem,
} from "./Parser.ts";
import {
  ADT,
  emptyTypeEnv,
  Scheme,
  TArr,
  TCon,
  TTuple,
  TVar,
  Type,
  typeBool,
  TypeEnv,
  typeInt,
  typeString,
  typeUnit,
} from "./Typing.ts";
import { mkTuple, RuntimeValue, tupleComponent } from "./Values.ts";

type RuntimeEnv = { [key: string]: RuntimeValue };

export const runtime = (env: Env): RuntimeEnv => env[0];

export const typeEnv = (env: Env): TypeEnv => env[1];

export type Env = [RuntimeEnv, TypeEnv];

const mkEnv = (
  runtime: RuntimeEnv,
  typeEnv: TypeEnv,
): Env => [runtime, typeEnv];

export const emptyRuntimeEnv: RuntimeEnv = {};

export const emptyEnv = mkEnv(emptyRuntimeEnv, emptyTypeEnv);

export const defaultEnv = mkEnv(
  {
    string_length: (s: string) => s.length,
    string_concat: (s1: string) => (s2: string) => s1 + s2,
    string_substring: (s: string) => (start: number) => (end: number) =>
      s.slice(start, end),
    string_equal: (s1: string) => (s2: string) => s1 === s2,
    string_compare: (s1: string) => (s2: string) =>
      s1 < s2 ? -1 : s1 === s2 ? 0 : 1,
  },
  emptyTypeEnv
    .extend(
      "string_length",
      new Scheme(new Set(), new TArr(typeString, typeInt)),
    )
    .extend(
      "string_concat",
      new Scheme(
        new Set(),
        new TArr(typeString, new TArr(typeString, typeString)),
      ),
    )
    .extend(
      "string_substring",
      new Scheme(
        new Set(),
        new TArr(typeString, new TArr(typeInt, new TArr(typeInt, typeString))),
      ),
    )
    .extend(
      "string_equal",
      new Scheme(
        new Set(),
        new TArr(typeString, new TArr(typeString, typeBool)),
      ),
    )
    .extend(
      "string_compare",
      new Scheme(
        new Set(),
        new TArr(typeString, new TArr(typeString, typeInt)),
      ),
    )
    .addData(new ADT("Int", new Set(), []))
    .addData(new ADT("String", new Set(), []))
    .addData(new ADT("Bool", new Set(), [])),
);

const binaryOps = new Map<
  number,
  (v1: RuntimeValue, v2: RuntimeValue) => RuntimeValue
>([
  [Op.Equals, (a, b) => a === b],
  [Op.Plus, (a, b) => (a + b) | 0],
  [Op.Minus, (a, b) => (a - b) | 0],
  [Op.Times, (a, b) => (a * b) | 0],
  [Op.Divide, (a, b) => (a / b) | 0],
]);

const evaluate = (expr: Expression, runtimeEnv: RuntimeEnv): RuntimeValue => {
  if (expr.type === "App") {
    const operator = evaluate(expr.e1, runtimeEnv);
    const operand = evaluate(expr.e2, runtimeEnv);
    return operator(operand);
  }
  if (expr.type === "If") {
    return evaluate(expr.guard, runtimeEnv)
      ? evaluate(expr.then, runtimeEnv)
      : evaluate(expr.else, runtimeEnv);
  }
  if (expr.type === "Lam") {
    return (x: RuntimeValue): RuntimeValue => {
      const newRuntimeEnv = { ...runtimeEnv };
      newRuntimeEnv[expr.name] = x;
      return evaluate(expr.expr, newRuntimeEnv);
    };
  }
  if (expr.type === "Let" || expr.type === "LetRec") {
    return executeDeclaration(expr, runtimeEnv)[0];
  }
  if (expr.type === "LBool") {
    return expr.value;
  }
  if (expr.type === "LInt") {
    return expr.value;
  }
  if (expr.type === "LString") {
    return expr.value;
  }
  if (expr.type === "LTuple") {
    return mkTuple(expr.values.map((v) => evaluate(v, runtimeEnv)));
  }
  if (expr.type === "LUnit") {
    return null;
  }
  if (expr.type === "Match") {
    const e = evaluate(expr.expr, runtimeEnv);

    for (const c of expr.cases) {
      const newEnv = matchPattern(c.pattern, e, runtimeEnv);
      if (newEnv !== null) {
        return evaluate(c.expr, newEnv);
      }
    }
    throw new Error("Match failed");
  }
  if (expr.type === "Op") {
    const left = evaluate(expr.left, runtimeEnv);
    const right = evaluate(expr.right, runtimeEnv);
    return binaryOps.get(expr.op)!(left, right);
  }
  if (expr.type === "Var") {
    return runtimeEnv[expr.name];
  }

  return null;
};

const matchPattern = (
  pattern: Pattern,
  value: RuntimeValue,
  runtimeEnv: RuntimeEnv,
): RuntimeEnv | null => {
  if (pattern.type === "PBool") {
    return pattern.value === value ? runtimeEnv : null;
  }
  if (pattern.type === "PInt") {
    return pattern.value === value ? runtimeEnv : null;
  }
  if (pattern.type === "PString") {
    return pattern.value === value ? runtimeEnv : null;
  }
  if (pattern.type === "PVar") {
    const newEnv = { ...runtimeEnv };
    newEnv[pattern.name] = value;
    return newEnv;
  }
  if (pattern.type === "PTuple") {
    let newRuntimeEnv: RuntimeEnv | null = runtimeEnv;
    for (let i = 0; i < pattern.values.length; i++) {
      newRuntimeEnv = matchPattern(
        pattern.values[i],
        tupleComponent(value, i),
        newRuntimeEnv,
      );
      if (newRuntimeEnv === null) {
        return null;
      }
    }
    return newRuntimeEnv;
  }
  if (pattern.type === "PUnit") {
    return value === null ? runtimeEnv : null;
  }
  if (pattern.type === "PWildcard") {
    return runtimeEnv;
  }
  return null;
};

const executeDeclaration = (
  expr: LetExpression | LetRecExpression,
  runtimeEnv: RuntimeEnv,
): [RuntimeValue, RuntimeEnv] => {
  const newRuntimeEnv = { ...runtimeEnv };
  const values: Array<RuntimeValue> = [];

  expr.declarations.forEach((d) => {
    const value = evaluate(d.expr, newRuntimeEnv);
    newRuntimeEnv[d.name] = value;
    values.push(value);
  });

  return (expr.expr === undefined)
    ? [values, newRuntimeEnv]
    : [evaluate(expr.expr, newRuntimeEnv), runtimeEnv];
};

const executeExpression = (
  expr: Expression,
  runtimeEnv: RuntimeEnv,
): [RuntimeValue, RuntimeEnv] =>
  (expr.type === "Let" || expr.type === "LetRec")
    ? executeDeclaration(expr, runtimeEnv)
    : [evaluate(expr, runtimeEnv), runtimeEnv];

const mkConstructorFunction = (name: string, arity: number): RuntimeValue => {
  if (arity === 0) {
    return [name];
  }
  if (arity === 1) {
    return (x1: RuntimeValue) => [name, x1];
  }
  if (arity === 2) {
    return (x1: RuntimeValue) => (x2: RuntimeValue) => [name, x1, x2];
  }
  if (arity === 3) {
    return (x1: RuntimeValue) => (x2: RuntimeValue) => (x3: RuntimeValue) => [
      name,
      x1,
      x2,
      x3,
    ];
  }
  if (arity === 4) {
    return (x1: RuntimeValue) =>
    (x2: RuntimeValue) =>
    (x3: RuntimeValue) =>
    (x4: RuntimeValue) => [name, x1, x2, x3, x4];
  }
  if (arity === 5) {
    return (x1: RuntimeValue) =>
    (x2: RuntimeValue) =>
    (x3: RuntimeValue) =>
    (x4: RuntimeValue) =>
    (x5: RuntimeValue) => [name, x1, x2, x3, x4, x5];
  }

  throw { type: "TooManyConstructorArgumentsErrors", name, arity };
};

const executeDataDeclaration = (
  dd: DataDeclaration,
  env: Env,
): [Array<ADT>, Env] => {
  const translate = (t: TypeItem): Type => {
    if (t.type === "TypeConstructor") {
      const tc = env[1].data(t.name);
      if (tc === undefined) {
        throw { type: "UnknownDataError", name: t.name };
      }
      if (t.arguments.length !== tc.parameters.size) {
        throw {
          type: "IncorrectTypeArguments",
          name: t.name,
          expected: tc.parameters.size,
          actual: t.arguments.length,
        };
      }

      return new TCon(tc.name, t.arguments.map(translate));
    }
    if (t.type === "TypeVariable") {
      return new TVar(t.name);
    }
    if (t.type === "TypeFunction") {
      return new TArr(translate(t.left), translate(t.right));
    }
    if (t.type === "TypeTuple") {
      return new TTuple(t.values.map(translate));
    }
    if (t.type === "TypeUnit") {
      return typeUnit;
    }

    throw { type: "UnknownTypeItemError", item: t };
  };

  const adts: Array<ADT> = [];

  dd.declarations.forEach((d) => {
    if (env[1].data(d.name) !== undefined) {
      throw { type: "DuplicateDataDeclaration", name: d.name };
    }

    const adt = new ADT(d.name, new Set(d.parameters), []);

    env = [env[0], env[1].addData(adt)];
  });

  dd.declarations.forEach((d) => {
    const adt = new ADT(
      d.name,
      new Set(d.parameters),
      d.constructors.map((c) => new TCon(c.name, c.parameters.map(translate))),
    );

    adts.push(adt);
    const runtimeEnv = env[0];
    let typeEnv = env[1].addData(adt);

    adt.constructors.forEach((c) => {
      typeEnv = typeEnv.extend(
        c.name,
        new Scheme(
          adt.parameters,
          c.args.reduceRight(
            (acc: Type, t: Type) => new TArr(t, acc),
            new TCon(
              adt.name,
              [...adt.parameters].map((p) => new TVar(p)),
            ),
          ),
        ),
      );

      runtimeEnv[c.name] = mkConstructorFunction(c.name, c.args.length);
    });

    env = [runtimeEnv, typeEnv];
  });

  return [adts, env];
};

export const executeProgram = (
  program: Program,
  env: Env,
): [Array<[RuntimeValue, Type | undefined]>, Env] => {
  const results: Array<[RuntimeValue, Type | undefined]> = [];

  program.forEach((e) => {
    if (e.type === "DataDeclaration") {
      const [adts, newEnv] = executeDataDeclaration(e, env);
      results.push([adts, undefined]);
      env = newEnv;
    } else {
      const [constraints, type, newTypeEnv] = inferExpression(typeEnv(env), e);
      const subst = constraints.solve();
      const newType = type.apply(subst);

      const [value, newRuntime] = executeExpression(e, runtime(env));

      results.push([value, newType]);
      env = mkEnv(newRuntime, newTypeEnv);
    }
  });

  return [results, env];
};

export const execute = (
  input: string,
  env: Env = emptyEnv,
): [Array<[RuntimeValue, Type | undefined]>, Env] =>
  executeProgram(parse(input), env);
