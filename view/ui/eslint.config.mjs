import eslint from '@eslint/js'
import eslintPluginReact from 'eslint-plugin-react'
import eslintPluginReactHooks from 'eslint-plugin-react-hooks'
import tseslint from 'typescript-eslint'

export default tseslint.config(
	{
		// might be able to find a way to not ignore some of these but i just ignore them
		ignores: [
			'**/dist',
			'**/vite.config.ts',
			'tailwind.config.js',
			'eslint.config.mjs',
			'postcss.config.js'
		],
	},
	{
		languageOptions: {
			parserOptions: {
				project: ['./tsconfig.json'], // <-- this file exists in vite projects, otherwise just use tsconfig.json
				tsconfigRootDir: import.meta.dirname,
			},
		},

		settings: {
			react: {
				version: 'detect',
			},
		},
	},
	eslint.configs.recommended,
	...tseslint.configs.recommended,
	//...tseslint.configs.stylisticTypeChecked,
	//...tseslint.configs.strictTypeChecked,
	eslintPluginReact.configs.flat.recommended,
	{
		plugins: {
			'react-hooks': eslintPluginReactHooks,
		},
		rules: eslintPluginReactHooks.configs.recommended.rules,
	},
	{
		rules: {
			// prefer template strings over string appends
			'prefer-template': 'off',

			// if you use 'new jsx transform' don't have to import React from 'react'
			'react/react-in-jsx-scope': 'off',

			"@typescript-eslint/no-unused-vars": [
				"error",
				{
					"args": "all",
					"argsIgnorePattern": "^_",
					"caughtErrors": "all",
					"caughtErrorsIgnorePattern": "^_",
					"destructuredArrayIgnorePattern": "^_",
					"varsIgnorePattern": "^_",
					"ignoreRestSiblings": true
				}
			],
			// would require redesign of sparql querying code
			"@typescript-eslint/no-explicit-any": "off",
		},
	},
)
