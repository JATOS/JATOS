/*! Bulma integration for DataTables' Responsive
 * © SpryMedia Ltd - datatables.net/license
 */

(function( factory ){
	if ( typeof define === 'function' && define.amd ) {
		// AMD
		define( ['jquery', 'datatables.net-bm', 'datatables.net-responsive'], function ( $ ) {
			return factory( $, window, document );
		} );
	}
	else if ( typeof exports === 'object' ) {
		// CommonJS
		var jq = require('jquery');
		var cjsRequires = function (root, $) {
			if ( ! $.fn.dataTable ) {
				require('datatables.net-bm')(root, $);
			}

			if ( ! $.fn.dataTable.Responsive ) {
				require('datatables.net-responsive')(root, $);
			}
		};

		if (typeof window === 'undefined') {
			module.exports = function (root, $) {
				if ( ! root ) {
					// CommonJS environments without a window global must pass a
					// root. This will give an error otherwise
					root = window;
				}

				if ( ! $ ) {
					$ = jq( root );
				}

				cjsRequires( root, $ );
				return factory( $, root, root.document );
			};
		}
		else {
			cjsRequires( window, jq );
			module.exports = factory( jq, window, window.document );
		}
	}
	else {
		// Browser
		factory( jQuery, window, document );
	}
}(function( $, window, document, undefined ) {
'use strict';
var DataTable = $.fn.dataTable;



var _display = DataTable.Responsive.display;
var _original = _display.modal;
var _modal = $(
	'<div class="modal DTED">' +
		'<div class="modal-background"></div>' +
		'<div class="modal-content">' +
		'<div class="modal-header">' +
		'<button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>' +
		'</div>' +
		'<div class="modal-body"/>' +
		'</div>' +
		'<button class="modal-close is-large" aria-label="close"></button>' +
		'</div>'
);

_display.modal = function (options) {
	return function (row, update, render, closeCallback) {
		if (!update) {
			if (options && options.header) {
				var header = _modal.find('div.modal-header');
				header.find('button').detach();

				header
					.empty()
					.append('<h4 class="modal-title subtitle">' + options.header(row) + '</h4>');
			}

			_modal.find('div.modal-body').empty().append(render());

			_modal.data('dtr-row-idx', row.index()).appendTo('body');

			_modal.addClass('is-active is-clipped');

			$('.modal-close').one('click', function () {
				_modal.removeClass('is-active is-clipped');
				closeCallback();
			});
			$('.modal-background').one('click', function () {
				_modal.removeClass('is-active is-clipped');
				closeCallback();
			});
		}
		else {
			if ($.contains(document, _modal[0]) && row.index() === _modal.data('dtr-row-idx')) {
				_modal.find('div.modal-body').empty().append(render());
			}
			else {
				// Modal not shown - do nothing
				return null;
			}
		}

		return true;
	};
};


return DataTable;
}));
