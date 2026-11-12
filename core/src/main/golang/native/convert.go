package main

//#include "bridge.h"
import "C"

import (
	"cfa/native/convert"
	"cfa/native/panicguard"
)

type convertResult struct {
	Yaml  string `json:"yaml,omitempty"`
	Error string `json:"error,omitempty"`
	// Узлы взяты не из ссылок, а из конфига, который ядро не приняло (convert.ConvertConfig).
	FromConfig bool `json:"fromConfig,omitempty"`
}

// convertAndApplyTemplate разбирает набор ссылок протоколов и подставляет узлы во
// встроенный шаблон приложения, отдавая готовый конфиг (D-19, D-34).
//
// Возвращает JSON: {"yaml": "…"} при успехе, {"error": "…"} при неудаче. Ошибка —
// обычный текст для показа человеку: по ссылке пришло что-то, что не разобралось.
// "fromConfig": true — узлы взяты из конфига с ошибкой, а не из ссылок.
//
//export convertAndApplyTemplate
func convertAndApplyTemplate(content, template, directHost C.c_string) *C.char {
	defer panicguard.Recover("convertAndApplyTemplate", failExport)
	out, fromConfig, err := convert.ApplyTemplate(C.GoString(content), C.GoString(template), C.GoString(directHost))
	if err != nil {
		return marshalJson(convertResult{Error: err.Error()})
	}

	return marshalJson(convertResult{Yaml: out, FromConfig: fromConfig})
}

// convertWithSources builds a config in template mode (D-493): the template with the
// subscription's nodes, providers and dialer-chain groups (convert.ApplyTemplateWithSources).
// The same JSON as convertAndApplyTemplate, without "fromConfig".
//
//export convertWithSources
func convertWithSources(source, template, directHost C.c_string) *C.char {
	defer panicguard.Recover("convertWithSources", failExport)
	out, err := convert.ApplyTemplateWithSources(C.GoString(source), C.GoString(template), C.GoString(directHost))
	if err != nil {
		return marshalJson(convertResult{Error: err.Error()})
	}

	return marshalJson(convertResult{Yaml: out})
}
